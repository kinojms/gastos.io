<script lang="ts">
  import { toast } from './toastState.svelte'
</script>

{#if toast.text}
  {#key toast.id}
    <div class="toast" role="status" aria-live="polite">{toast.text}</div>
  {/key}
{/if}

<style>
  .toast {
    position: fixed;
    left: 50%;
    bottom: calc(90px + env(safe-area-inset-bottom));
    transform: translateX(-50%);
    max-width: calc(100vw - 32px);
    padding: 12px 18px;
    border-radius: 12px;
    background: var(--fg);
    color: var(--bg);
    font-size: 14px;
    text-align: center;
    z-index: 60;
    pointer-events: none;
    animation: pop 0.18s ease-out;
  }

  @keyframes pop {
    from {
      opacity: 0;
      transform: translate(-50%, 8px);
    }
  }
</style>
