<script lang="ts">
  import { router } from './lib/router.svelte'
  import Toast from './lib/ui/Toast.svelte'
  import TopBar from './lib/ui/TopBar.svelte'
  import Home from './screens/Home.svelte'
</script>

<div class="app safe">
  {#if router.route.name === 'home'}
    <main class="home-main"><Home /></main>
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
  }

  main {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    padding: 0 16px;
  }

  .home-main {
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }
</style>
