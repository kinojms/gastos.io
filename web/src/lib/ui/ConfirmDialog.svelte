<script lang="ts">
  import { dismissOverlay, openOverlay, type OverlayEntry } from './overlays'

  let {
    open,
    message,
    confirmText = 'Confirm',
    danger = false,
    onConfirm,
    onCancel,
  }: {
    open: boolean
    message: string
    confirmText?: string
    danger?: boolean
    onConfirm: () => void
    onCancel: () => void
  } = $props()

  let entry: OverlayEntry | null = null
  let confirmButton: HTMLButtonElement | undefined = $state()

  $effect(() => {
    if (open && !entry) {
      entry = openOverlay(() => {
        entry = null
        onCancel()
      })
      queueMicrotask(() => confirmButton?.focus())
    } else if (!open && entry) {
      dismissOverlay(entry)
      entry = null
    }
  })

  $effect(() => () => {
    if (entry) {
      dismissOverlay(entry)
      entry = null
    }
  })

  function onKey(e: KeyboardEvent) {
    if (e.key === 'Escape') onCancel()
    if (e.key !== 'Tab') return
    const buttons = [...(e.currentTarget as HTMLElement).querySelectorAll('button')]
    const first = buttons[0]
    const last = buttons[buttons.length - 1]
    if (e.shiftKey && document.activeElement === first) {
      e.preventDefault()
      last.focus()
    } else if (!e.shiftKey && document.activeElement === last) {
      e.preventDefault()
      first.focus()
    }
  }
</script>

{#if open}
  <div class="scrim" role="presentation" onclick={onCancel}>
    <div
      class="dialog"
      role="alertdialog"
      aria-modal="true"
      aria-label={message}
      tabindex="-1"
      onclick={(e) => e.stopPropagation()}
      onkeydown={onKey}
    >
      <p>{message}</p>
      <div class="actions">
        <button class="text muted" onclick={onCancel}>Cancel</button>
        <button class="text" class:danger bind:this={confirmButton} onclick={onConfirm}>{confirmText}</button>
      </div>
    </div>
  </div>
{/if}

<style>
  .scrim {
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.5);
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 24px;
    z-index: 50;
  }

  .dialog {
    width: 100%;
    max-width: 340px;
    background: var(--surface);
    color: var(--fg);
    border-radius: 24px;
    padding: 24px 24px 12px;
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.35);
  }

  p {
    margin: 0 0 20px;
    font-size: 16px;
    line-height: 1.4;
    overflow-wrap: anywhere;
  }

  .actions {
    display: flex;
    justify-content: flex-end;
    gap: 4px;
  }

  .text {
    background: none;
    border: 0;
    padding: 10px 14px;
    border-radius: 12px;
    font-size: 15px;
    font-weight: 600;
    color: var(--accent);
  }

  .text.muted {
    color: var(--muted);
  }

  .text.danger {
    color: var(--danger);
  }
</style>
