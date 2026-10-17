<script lang="ts">
  import type { Snippet } from 'svelte'
  import { dismissOverlay, openOverlay, type OverlayEntry } from './overlays'

  let { open, onClose, children }: { open: boolean; onClose: () => void; children: Snippet } = $props()

  let entry: OverlayEntry | null = null
  let dragStart: number | null = null
  let dragY = $state(0)

  $effect(() => {
    if (open && !entry) {
      entry = openOverlay(() => {
        entry = null
        onClose()
      })
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

  function down(e: PointerEvent) {
    dragStart = e.clientY
    ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
  }

  function move(e: PointerEvent) {
    if (dragStart !== null) dragY = Math.max(0, e.clientY - dragStart)
  }

  function up() {
    if (dragY > 80) onClose()
    dragStart = null
    dragY = 0
  }
</script>

{#if open}
  <div class="scrim" role="presentation" onclick={onClose}>
    <div
      class="sheet safe-bottom"
      role="dialog"
      aria-modal="true"
      tabindex="-1"
      style:transform={dragY ? `translateY(${dragY}px)` : undefined}
      onclick={(e) => e.stopPropagation()}
      onkeydown={(e) => e.key === 'Escape' && onClose()}
    >
      <div
        class="grab"
        role="presentation"
        onpointerdown={down}
        onpointermove={move}
        onpointerup={up}
        onpointercancel={up}
      >
        <span></span>
      </div>
      {@render children()}
    </div>
  </div>
{/if}

<style>
  .scrim {
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.5);
    display: flex;
    align-items: flex-end;
    justify-content: center;
    z-index: 40;
  }

  .sheet {
    width: 100%;
    max-width: 520px;
    max-height: 92dvh;
    overflow-y: auto;
    background: var(--surface);
    color: var(--fg);
    border-radius: 24px 24px 0 0;
    padding: 0 18px;
    animation: rise 0.2s ease-out;
  }

  .safe-bottom {
    padding-bottom: calc(18px + env(safe-area-inset-bottom));
  }

  .grab {
    display: flex;
    justify-content: center;
    padding: 12px 0 8px;
    touch-action: none;
  }

  .grab span {
    width: 40px;
    height: 4px;
    border-radius: 2px;
    background: var(--line);
  }

  @keyframes rise {
    from {
      transform: translateY(40px);
      opacity: 0;
    }
  }
</style>
