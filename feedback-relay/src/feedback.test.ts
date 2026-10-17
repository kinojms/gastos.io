import { afterEach, describe, expect, it, vi } from 'vitest'
import { corsHeaders, escapeHtml, formatMessage, parseFeedback } from './feedback'
import worker from './index'

const valid = { type: 'Bug report', message: ' hi ', contact: ' me ', appVersion: '1.4.0', platform: 'Android' }

describe('parseFeedback', () => {
  it('accepts a valid body and trims text', () => {
    expect(parseFeedback(valid)).toEqual({ type: 'Bug report', message: 'hi', contact: 'me', appVersion: '1.4.0', platform: 'Android' })
  })

  it('rejects each bad field', () => {
    const bad = [
      { ...valid, type: 'Rant' },
      { ...valid, message: '   ' },
      { ...valid, message: 'x'.repeat(2001) },
      { ...valid, contact: 'x'.repeat(101) },
      { ...valid, appVersion: 'x'.repeat(21) },
      { ...valid, platform: 'Mac' },
      { ...valid, message: 5 },
      'hello',
      null,
      [valid],
    ]
    for (const body of bad) expect(parseFeedback(body), JSON.stringify(body)).toBeNull()
  })

  it('treats a missing contact as empty', () => {
    const { contact: _c, ...rest } = valid
    expect(parseFeedback(rest)?.contact).toBe('')
  })
})

describe('formatMessage', () => {
  it('lays out the message in Manila time with all user text escaped', () => {
    const text = formatMessage(
      { type: 'Bug report', message: '<b>hi</b> & bye', contact: '', appVersion: '1.4.0', platform: 'Android' },
      new Date('2026-10-17T06:30:00Z'),
    )
    expect(text).toBe(
      [
        '[FEEDBACK - Gastos.io]',
        'Type: Bug report',
        'From: —',
        'App: v1.4.0 · Android',
        'Time: 2026-10-17 14:30 (Asia/Manila)',
        '',
        '&lt;b&gt;hi&lt;/b&gt; &amp; bye',
      ].join('\n'),
    )
  })

  it('escapes the contact too', () => {
    expect(formatMessage({ ...valid, contact: '<a href=x>' } as never, new Date(0))).toContain('From: &lt;a href=x&gt;')
  })

  it('escapes the three HTML specials', () => expect(escapeHtml('<&>"')).toBe('&lt;&amp;&gt;"'))
})

describe('corsHeaders', () => {
  it('allows the Pages site and local dev only', () => {
    expect(corsHeaders('https://kinojms.github.io')['Access-Control-Allow-Origin']).toBe('https://kinojms.github.io')
    expect(corsHeaders('http://localhost:4173')['Access-Control-Allow-Origin']).toBe('http://localhost:4173')
    expect(corsHeaders('https://evil.example')['Access-Control-Allow-Origin']).toBeUndefined()
    expect(corsHeaders(null)['Access-Control-Allow-Origin']).toBeUndefined()
  })
})

describe('worker', () => {
  const env = { TELEGRAM_BOT_TOKEN: '123:SECRET', TELEGRAM_CHAT_ID: '42' }
  const post = (body: string) =>
    new Request('https://relay.test/', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Origin: 'https://kinojms.github.io' },
      body,
    })

  afterEach(() => vi.unstubAllGlobals())

  it('forwards a valid post to Telegram once and answers ok', async () => {
    const tg = vi.fn(async () => new Response('{"ok":true}', { status: 200 }))
    vi.stubGlobal('fetch', tg)
    const res = await worker.fetch(post(JSON.stringify(valid)), env)
    expect(res.status).toBe(200)
    expect(await res.json()).toEqual({ ok: true })
    expect(res.headers.get('Access-Control-Allow-Origin')).toBe('https://kinojms.github.io')
    expect(tg).toHaveBeenCalledTimes(1)
    const [url, init] = tg.mock.calls[0] as unknown as [string, RequestInit]
    expect(url).toBe('https://api.telegram.org/bot123:SECRET/sendMessage')
    const sent = JSON.parse(String(init.body))
    expect(sent).toMatchObject({ chat_id: '42', parse_mode: 'HTML', disable_web_page_preview: true })
    expect(sent.text).toContain('[FEEDBACK - Gastos.io]')
  })

  it('answers 502 without leaking the token when Telegram fails', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('{"ok":false,"description":"bot123:SECRET bad"}', { status: 400 })))
    const res = await worker.fetch(post(JSON.stringify(valid)), env)
    expect(res.status).toBe(502)
    expect(await res.text()).not.toContain('SECRET')
  })

  it('answers 502 when Telegram is unreachable', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => {
      throw new TypeError('network')
    }))
    expect((await worker.fetch(post(JSON.stringify(valid)), env)).status).toBe(502)
  })

  it('rejects oversized, invalid and non-JSON bodies', async () => {
    const tg = vi.fn()
    vi.stubGlobal('fetch', tg)
    expect((await worker.fetch(post('x'.repeat(9000)), env)).status).toBe(413)
    expect((await worker.fetch(post(JSON.stringify({ ...valid, type: 'Nope' })), env)).status).toBe(400)
    expect((await worker.fetch(post('not json'), env)).status).toBe(400)
    expect(tg).not.toHaveBeenCalled()
  })

  it('answers preflight and refuses other methods', async () => {
    const pre = await worker.fetch(
      new Request('https://relay.test/', { method: 'OPTIONS', headers: { Origin: 'https://kinojms.github.io' } }),
      env,
    )
    expect(pre.status).toBe(204)
    expect(pre.headers.get('Access-Control-Allow-Methods')).toContain('POST')
    expect((await worker.fetch(new Request('https://relay.test/'), env)).status).toBe(405)
  })
})
