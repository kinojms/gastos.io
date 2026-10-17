<script lang="ts">
  import { confirmCopy } from '../lib/domain/confirmCopy'
  import {
    CONTACT_MAX, FEEDBACK_TYPES, MESSAGE_MAX, buildFeedback, feedbackPlatform, sendFeedback, type FeedbackType,
  } from '../lib/feedback/send'
  import { installState } from '../lib/install'
  import { back } from '../lib/router.svelte'
  import ConfirmDialog from '../lib/ui/ConfirmDialog.svelte'
  import { showToast } from '../lib/ui/toastState.svelte'
  import TopBar from '../lib/ui/TopBar.svelte'

  let type = $state<FeedbackType>('Suggestion')
  let message = $state('')
  let contact = $state('')
  let busy = $state(false)
  let confirming = $state(false)
  let error = $state<string | null>(null)
  /** Set when the user leaves mid-send, so a late success doesn't navigate back a second time. */
  let left = false
  $effect(() => () => {
    left = true
  })

  const canSend = $derived(message.trim().length > 0 && !busy)

  async function send() {
    confirming = false
    busy = true
    error = null
    const result = await sendFeedback(buildFeedback(type, message, contact, __APP_VERSION__, feedbackPlatform(installState())))
    busy = false
    if (result.ok) {
      message = ''
      contact = ''
      type = 'Suggestion'
      showToast('Thanks! Your feedback was sent.')
      if (!left) back()
    } else {
      error = result.message
    }
  }
</script>

<TopBar title="Submit feedback" />
<div class="page">
  <label>
    <span>Type</span>
    <select bind:value={type}>
      {#each FEEDBACK_TYPES as t (t)}<option value={t}>{t}</option>{/each}
    </select>
  </label>

  <label>
    <span>Message</span>
    <textarea
      rows="8"
      maxlength={MESSAGE_MAX}
      placeholder="Tell us what's on your mind…"
      bind:value={message}
      oninput={() => (error = null)}
    ></textarea>
    <span class="counter">{message.length} / {MESSAGE_MAX}</span>
  </label>

  <label>
    <span>Your name or contact (optional)</span>
    <input type="text" maxlength={CONTACT_MAX} autocomplete="off" bind:value={contact} />
    <span class="hint">So we can get back to you</span>
  </label>

  <button class="send" disabled={!canSend} onclick={() => (confirming = true)}>{busy ? 'Sending…' : 'Send'}</button>
  {#if error}<p class="error" role="alert">{error}</p>{/if}
</div>

<ConfirmDialog
  open={confirming}
  message={confirmCopy.sendFeedback()}
  confirmText="Send"
  onConfirm={send}
  onCancel={() => (confirming = false)}
/>

<style>
  .page {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    padding: 0 16px 24px;
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  label {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  label > span {
    font-size: 12px;
    color: var(--muted);
  }

  select,
  textarea,
  input {
    width: 100%;
    min-height: 48px;
    padding: 10px 12px;
    border: 1px solid var(--line);
    border-radius: 12px;
    background: var(--surface);
    color: var(--fg);
  }

  textarea {
    resize: vertical;
  }

  .counter {
    align-self: flex-end;
  }

  .send {
    height: 52px;
    border: 0;
    border-radius: 14px;
    background: var(--accent);
    color: var(--on-accent);
    font-weight: 700;
    font-size: 16px;
  }

  .send:disabled {
    opacity: 0.5;
  }

  .error {
    margin: 0;
    color: var(--danger);
    font-size: 14px;
  }
</style>
