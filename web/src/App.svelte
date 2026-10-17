<script lang="ts">
  import { router } from './lib/router.svelte'
  import Toast from './lib/ui/Toast.svelte'
  import TopBar from './lib/ui/TopBar.svelte'
</script>

<div class="app safe">
  {#if router.route.name === 'home'}
    <main><p>Home</p></main>
  {:else}
    <TopBar title={router.route.name} />
    <main><p>{router.route.name}</p></main>
  {/if}
</div>
<Toast />

<style>
  .app {
    height: 100%;
    display: flex;
    flex-direction: column;
    background: var(--bg);
  }

  main {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    padding: 0 16px;
  }
</style>
