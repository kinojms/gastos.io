export type VoiceLanguageId = 'ENGLISH' | 'TAGALOG'

/** Same names and tags as Android's VoiceLanguage. Stored by id. */
export const VOICE_LANGUAGES: { id: VoiceLanguageId; name: string; tag: string }[] = [
  { id: 'ENGLISH', name: 'English (Philippines)', tag: 'en-PH' },
  { id: 'TAGALOG', name: 'Tagalog', tag: 'fil-PH' },
]

export function voiceLanguage(id: string | undefined) {
  return VOICE_LANGUAGES.find((l) => l.id === id) ?? VOICE_LANGUAGES[0]
}

/** Speech recognition can't run here (blocked, no microphone, offline). Typing always works. */
export class VoiceUnavailable extends Error {}

interface RecognitionResultEvent {
  results: ArrayLike<ArrayLike<{ transcript: string }>>
}

interface Recognition {
  lang: string
  interimResults: boolean
  maxAlternatives: number
  onresult: ((e: RecognitionResultEvent) => void) | null
  onerror: ((e: { error: string }) => void) | null
  onend: (() => void) | null
  start(): void
  stop(): void
  abort(): void
}

type RecognitionCtor = new () => Recognition

function ctor(): RecognitionCtor | undefined {
  const w = window as unknown as { SpeechRecognition?: RecognitionCtor; webkitSpeechRecognition?: RecognitionCtor }
  return w.SpeechRecognition ?? w.webkitSpeechRecognition
}

export function voiceSupported(): boolean {
  return ctor() !== undefined
}

const UNAVAILABLE_ERRORS = new Set(['not-allowed', 'service-not-allowed', 'audio-capture', 'network'])

let active: Recognition | null = null

/** Stops the recording in progress, if any. Its promise then resolves with null. */
export function stopListening(): void {
  active?.abort()
}

/**
 * Listens once. Resolves with the transcript, or null when nothing was heard or it was stopped.
 * Rejects with [VoiceUnavailable] when recognition is blocked or failing.
 */
export function listenOnce(lang: string): Promise<string | null> {
  const Ctor = ctor()
  if (!Ctor) return Promise.reject(new VoiceUnavailable('Speech recognition is not supported.'))
  return new Promise((resolve, reject) => {
    const rec = new Ctor()
    active = rec
    let transcript: string | null = null
    let failure: VoiceUnavailable | null = null
    rec.lang = lang
    rec.interimResults = false
    rec.maxAlternatives = 1
    rec.onresult = (e) => {
      transcript = e.results[0]?.[0]?.transcript ?? null
    }
    rec.onerror = (e) => {
      if (UNAVAILABLE_ERRORS.has(e.error)) failure = new VoiceUnavailable(e.error)
    }
    rec.onend = () => {
      if (active === rec) active = null
      if (failure) reject(failure)
      else resolve(transcript)
    }
    try {
      rec.start()
    } catch {
      active = null
      reject(new VoiceUnavailable('Could not start recognition.'))
    }
  })
}
