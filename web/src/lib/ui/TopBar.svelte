<script lang="ts">
  import type { Snippet } from 'svelte'
  import { back } from '../router.svelte'

  let { title, onBack = back, actions }: { title: string; onBack?: () => void; actions?: Snippet } = $props()
</script>

<header>
  <button class="back" aria-label="Back" onclick={onBack}>
    <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
      <path d="M15 5l-7 7 7 7" />
    </svg>
  </button>
  <h1>{title}</h1>
  {#if actions}<div class="actions">{@render actions()}</div>{/if}
</header>

<style>
  header {
    display: flex;
    align-items: center;
    gap: 4px;
    padding: 6px 8px;
  }

  h1 {
    flex: 1;
    margin: 0;
    font-size: 18px;
    font-weight: 700;
  }

  .back {
    width: 44px;
    height: 44px;
    display: grid;
    place-items: center;
    border: 0;
    background: none;
    border-radius: 22px;
  }

  .actions {
    display: flex;
    gap: 8px;
  }
</style>
