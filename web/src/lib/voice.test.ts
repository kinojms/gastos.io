import { afterEach, describe, expect, it } from 'vitest'
import { VOICE_LANGUAGES, VoiceUnavailable, listenOnce, stopListening, voiceSupported } from './voice'

type Handlers = {
  onresult: ((e: any) => void) | null
  onerror: ((e: any) => void) | null
  onend: (() => void) | null
}

/** A fake recognizer; `script` decides what happens after start(). */
function install(script: (r: any) => void) {
  class FakeRecognition implements Handlers {
    lang = ''
    interimResults = true
    maxAlternatives = 5
    onresult: Handlers['onresult'] = null
    onerror: Handlers['onerror'] = null
    onend: Handlers['onend'] = null
    start() {
      setTimeout(() => script(this), 0)
    }
    stop() {}
    abort() {
      this.onend?.()
    }
  }
  ;(window as any).webkitSpeechRecognition = FakeRecognition
  return FakeRecognition
}

afterEach(() => {
  delete (window as any).webkitSpeechRecognition
  delete (window as any).SpeechRecognition
})

describe('voice', () => {
  it('lists Android languages', () => {
    expect(VOICE_LANGUAGES.map((l) => [l.id, l.name, l.tag])).toEqual([
      ['ENGLISH', 'English (Philippines)', 'en-PH'],
      ['TAGALOG', 'Tagalog', 'fil-PH'],
    ])
  })

  it('reports support from the presence of the API', () => {
    expect(voiceSupported()).toBe(false)
    install(() => {})
    expect(voiceSupported()).toBe(true)
  })

  it('resolves the transcript and passes the language', async () => {
    let lang = ''
    install((r) => {
      lang = r.lang
      r.onresult?.({ results: [[{ transcript: 'Jollibee' }]] })
      r.onend?.()
    })
    expect(await listenOnce('fil-PH')).toBe('Jollibee')
    expect(lang).toBe('fil-PH')
  })

  it('resolves null when nothing was heard', async () => {
    install((r) => {
      r.onerror?.({ error: 'no-speech' })
      r.onend?.()
    })
    expect(await listenOnce('en-PH')).toBeNull()
  })

  it('rejects with VoiceUnavailable when blocked', async () => {
    install((r) => {
      r.onerror?.({ error: 'not-allowed' })
      r.onend?.()
    })
    await expect(listenOnce('en-PH')).rejects.toBeInstanceOf(VoiceUnavailable)
  })

  it('rejects when the API is missing', async () => {
    await expect(listenOnce('en-PH')).rejects.toBeInstanceOf(VoiceUnavailable)
  })

  it('stopListening ends the recording with null', async () => {
    install(() => {})
    const pending = listenOnce('en-PH')
    await new Promise((r) => setTimeout(r, 5))
    stopListening()
    expect(await pending).toBeNull()
  })
})
