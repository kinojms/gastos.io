import { corsHeaders, formatMessage, parseFeedback } from './feedback'

export interface Env {
  TELEGRAM_BOT_TOKEN: string
  TELEGRAM_CHAT_ID: string
}

const MAX_BODY_BYTES = 8192

/**
 * Gastos.io feedback relay: validates feedback from the apps and forwards it to the owner's Telegram chat.
 * The bot token lives only in this Worker's secrets.
 */
export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const cors = corsHeaders(request.headers.get('Origin'))
    const reply = (status: number, body?: object) =>
      new Response(body ? JSON.stringify(body) : null, {
        status,
        headers: { ...cors, ...(body ? { 'Content-Type': 'application/json' } : {}) },
      })

    if (request.method === 'OPTIONS') return reply(204)
    if (request.method !== 'POST') return reply(405, { ok: false })

    // Refuse a declared oversize body before reading any of it; still check the real size after.
    const declared = Number(request.headers.get('Content-Length') ?? '0')
    if (declared > MAX_BODY_BYTES) return reply(413, { ok: false })
    const raw = await request.text()
    if (new TextEncoder().encode(raw).length > MAX_BODY_BYTES) return reply(413, { ok: false })

    let body: unknown
    try {
      body = JSON.parse(raw)
    } catch {
      return reply(400, { ok: false })
    }
    const feedback = parseFeedback(body)
    if (!feedback) return reply(400, { ok: false })

    try {
      const res = await fetch(`https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/sendMessage`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          chat_id: env.TELEGRAM_CHAT_ID,
          text: formatMessage(feedback, new Date()),
          parse_mode: 'HTML',
          disable_web_page_preview: true,
        }),
      })
      // Never pass Telegram's response through: it can echo the token.
      return res.ok ? reply(200, { ok: true }) : reply(502, { ok: false })
    } catch {
      return reply(502, { ok: false })
    }
  },
}
