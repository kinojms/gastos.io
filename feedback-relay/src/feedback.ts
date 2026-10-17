export const TYPES = ['Suggestion', 'Bug report', 'Question', 'Something else'] as const
export const PLATFORMS = ['Android', 'iPhone', 'Web'] as const

export const MESSAGE_MAX = 2000
export const CONTACT_MAX = 100
export const VERSION_MAX = 20

export interface Feedback {
  type: (typeof TYPES)[number]
  message: string
  contact: string
  appVersion: string
  platform: (typeof PLATFORMS)[number]
}

const ALLOWED_ORIGINS = ['https://kinojms.github.io', 'http://localhost:5173', 'http://localhost:4173']

/** The validated feedback, or null when anything is missing, too long or not one of the known values. */
export function parseFeedback(body: unknown): Feedback | null {
  if (typeof body !== 'object' || body === null || Array.isArray(body)) return null
  const b = body as Record<string, unknown>
  const contactRaw = b.contact === undefined ? '' : b.contact
  if (typeof b.type !== 'string' || typeof b.message !== 'string' || typeof contactRaw !== 'string') return null
  if (typeof b.appVersion !== 'string' || typeof b.platform !== 'string') return null
  if (!(TYPES as readonly string[]).includes(b.type)) return null
  if (!(PLATFORMS as readonly string[]).includes(b.platform)) return null
  const message = b.message.trim()
  const contact = contactRaw.trim()
  const appVersion = b.appVersion.trim()
  if (message.length === 0 || message.length > MESSAGE_MAX) return null
  if (contact.length > CONTACT_MAX || appVersion.length > VERSION_MAX) return null
  return { type: b.type as Feedback['type'], message, contact, appVersion, platform: b.platform as Feedback['platform'] }
}

/** Telegram HTML mode only needs these three escaped. */
export function escapeHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

/** "2026-10-17 14:30" in Asia/Manila (UTC+8, no daylight saving). */
function manilaStamp(d: Date): string {
  const m = new Date(d.getTime() + 8 * 3600 * 1000)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${m.getUTCFullYear()}-${pad(m.getUTCMonth() + 1)}-${pad(m.getUTCDate())} ${pad(m.getUTCHours())}:${pad(m.getUTCMinutes())}`
}

export function formatMessage(f: Feedback, receivedAt: Date): string {
  return [
    '[FEEDBACK - Gastos.io]',
    `Type: ${escapeHtml(f.type)}`,
    `From: ${f.contact ? escapeHtml(f.contact) : '—'}`,
    `App: v${escapeHtml(f.appVersion)} · ${escapeHtml(f.platform)}`,
    `Time: ${manilaStamp(receivedAt)} (Asia/Manila)`,
    '',
    escapeHtml(f.message),
  ].join('\n')
}

/** CORS for the web app and local dev. The Android app sends no Origin and needs none. */
export function corsHeaders(origin: string | null): Record<string, string> {
  const headers: Record<string, string> = {
    'Access-Control-Allow-Methods': 'POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type',
    'Access-Control-Max-Age': '86400',
    Vary: 'Origin',
  }
  if (origin && ALLOWED_ORIGINS.includes(origin)) headers['Access-Control-Allow-Origin'] = origin
  return headers
}
