<script lang="ts">
  import { onMount } from 'svelte'
  import { prefsStore } from '../data/prefs'
  import { cleanDescription } from '../domain/expenseText'
  import { VoiceUnavailable, listenOnce, stopListening, voiceLanguage, voiceSupported } from '../voice'
  import { showToast } from './toastState.svelte'

  /** Called with the cleaned transcript; it replaces the field's text. */
  let { onresult, disabled = false }: { onresult: (text: string) => void; disabled?: boolean } = $props()

  let supported = $state(false)
  let listening = $state(false)

  onMount(() => {
    supported = voiceSupported()
    return () => stopListening()
  })

  async function tap() {
    if (disabled) return
    if (listening) {
      stopListening()
      return
    }
    let lang = 'en-PH'
    const unsubscribe = prefsStore.subscribe((p) => (lang = voiceLanguage(p.voice_language).tag))
    unsubscribe()
    listening = true
    try {
      const heard = cleanDescription(await listenOnce(lang))
      if (heard) onresult(heard)
    } catch (e) {
      if (e instanceof VoiceUnavailable) showToast("Voice input isn't available here")
    } finally {
      listening = false
    }
  }
</script>

{#if supported}
  <button
    class="mic"
    class:listening
    type="button"
    aria-label={listening ? 'Stop listening' : 'Speak description'}
    aria-pressed={listening}
    onclick={tap}
  >
    <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
      <rect x="9" y="3" width="6" height="12" rx="3" />
      <path d="M5 11a7 7 0 0 0 14 0M12 18v3" />
    </svg>
  </button>
{/if}

<style>
  .mic {
    width: 44px;
    height: 44px;
    display: grid;
    place-items: center;
    border: 0;
    border-radius: 22px;
    background: none;
    color: var(--muted);
  }

  .mic.listening {
    color: var(--danger);
    animation: pulse 1s ease-in-out infinite;
  }

  @keyframes pulse {
    50% {
      opacity: 0.4;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .mic.listening {
      animation: none;
    }
  }
</style>
