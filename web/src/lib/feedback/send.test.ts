import { afterEach, describe, expect, it, vi } from 'vitest'
import { FAILED, FEEDBACK_TYPES, NO_CONNECTION, buildFeedback, feedbackPlatform, sendFeedback } from './send'

const body = buildFeedback('Bug report', '  it broke  ', '  ', '1.4.0', 'iPhone')

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('feedback payload', () => {
  it('lists the four types in order', () => {
    expect(FEEDBACK_TYPES).toEqual(['Suggestion', 'Bug report', 'Question', 'Something else'])
  })

  it('trims text and sends an empty contact as ""', () => {
    expect(body).toEqual({ type: 'Bug report', message: 'it broke', contact: '', appVersion: '1.4.0', platform: 'iPhone' })
  })

  it('names the platform from the install state', () => {
    expect(feedbackPlatform('ios-safari')).toBe('iPhone')
    expect(feedbackPlatform('installed')).toBe('iPhone')
    expect(feedbackPlatform('other')).toBe('Web')
  })
})

describe('sendFeedback', () => {
  it('posts JSON and reports success', async () => {
    const fetchFn = vi.fn(async () => new Response('{"ok":true}', { status: 200 }))
    expect(await sendFeedback(body, 'https://relay.test/', fetchFn as unknown as typeof fetch)).toEqual({ ok: true })
    const [url, init] = fetchFn.mock.calls[0] as unknown as [string, RequestInit]
    expect(url).toBe('https://relay.test/')
    expect(init.method).toBe('POST')
    expect(new Headers(init.headers).get('Content-Type')).toBe('application/json')
    expect(JSON.parse(String(init.body))).toEqual(body)
  })

  it('reports no connection when the request cannot be made', async () => {
    const fetchFn = vi.fn(async () => {
      throw new TypeError('Failed to fetch')
    })
    expect(await sendFeedback(body, 'https://relay.test/', fetchFn as unknown as typeof fetch)).toEqual({ ok: false, message: NO_CONNECTION })
  })

  it('reports no connection right away when the browser is offline', async () => {
    vi.stubGlobal('navigator', { onLine: false })
    const fetchFn = vi.fn()
    expect(await sendFeedback(body, 'https://relay.test/', fetchFn as unknown as typeof fetch)).toEqual({ ok: false, message: NO_CONNECTION })
    expect(fetchFn).not.toHaveBeenCalled()
  })

  it('reports a failure on a non-2xx answer', async () => {
    const fetchFn = vi.fn(async () => new Response('{"ok":false}', { status: 502 }))
    expect(await sendFeedback(body, 'https://relay.test/', fetchFn as unknown as typeof fetch)).toEqual({ ok: false, message: FAILED })
  })

  it('gives up after the timeout', async () => {
    vi.useFakeTimers()
    const fetchFn = vi.fn(
      (_url: string, init: RequestInit) =>
        new Promise<Response>((_resolve, reject) => {
          init.signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))
        }),
    )
    const pending = sendFeedback(body, 'https://relay.test/', fetchFn as unknown as typeof fetch, 15_000)
    await vi.advanceTimersByTimeAsync(15_001)
    expect(await pending).toEqual({ ok: false, message: FAILED })
  })

  it('copies the exact user-facing messages', () => {
    expect(NO_CONNECTION).toBe("No internet connection. Try again when you're online.")
    expect(FAILED).toBe("Couldn't send feedback. Try again later.")
  })
})
