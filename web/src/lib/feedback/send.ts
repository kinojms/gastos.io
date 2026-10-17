import type { InstallState } from '../install'
import { FEEDBACK_URL } from './config'

export const FEEDBACK_TYPES = ['Suggestion', 'Bug report', 'Question', 'Something else'] as const
export type FeedbackType = (typeof FEEDBACK_TYPES)[number]

export const MESSAGE_MAX = 2000
export const CONTACT_MAX = 100

export const NO_CONNECTION = "No internet connection. Try again when you're online."
export const FAILED = "Couldn't send feedback. Try again later."

export interface FeedbackBody {
  type: FeedbackType
  message: string
  contact: string
  appVersion: string
  platform: 'iPhone' | 'Web'
}

export function buildFeedback(
  type: FeedbackType,
  message: string,
  contact: string,
  appVersion: string,
  platform: FeedbackBody['platform'],
): FeedbackBody {
  return { type, message: message.trim(), contact: contact.trim(), appVersion, platform }
}

/** The web app is made for iPhone; anything else is reported as "Web". */
export function feedbackPlatform(state: InstallState): FeedbackBody['platform'] {
  return state === 'other' ? 'Web' : 'iPhone'
}

export type SendResult = { ok: true } | { ok: false; message: string }

/** Posts feedback to the relay. Never throws. */
export async function sendFeedback(
  body: FeedbackBody,
  url: string = FEEDBACK_URL,
  fetchFn: typeof fetch = (...a) => fetch(...a),
  timeoutMs = 15_000,
): Promise<SendResult> {
  if (typeof navigator !== 'undefined' && navigator.onLine === false) return { ok: false, message: NO_CONNECTION }
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  try {
    const res = await fetchFn(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      signal: controller.signal,
    })
    return res.ok ? { ok: true } : { ok: false, message: FAILED }
  } catch (e) {
    // A TypeError means the request never got out (offline, DNS, blocked); an abort is our timeout.
    return { ok: false, message: e instanceof TypeError ? NO_CONNECTION : FAILED }
  } finally {
    clearTimeout(timer)
  }
}
