<script lang="ts">
  import { router } from './lib/router.svelte'
  import Toast from './lib/ui/Toast.svelte'
  import DayView from './screens/DayView.svelte'
  import Expenses from './screens/Expenses.svelte'
  import Feedback from './screens/Feedback.svelte'
  import Home from './screens/Home.svelte'
  import Labels from './screens/Labels.svelte'
  import Methods from './screens/Methods.svelte'
  import Settings from './screens/Settings.svelte'

  const route = $derived(router.route)
</script>

<div class="app safe">
  {#if route.name === 'home'}
    <main class="home-main"><Home /></main>
  {:else if route.name === 'expenses'}
    <Expenses />
  {:else if route.name === 'day'}
    {#key route.date}<DayView day={route.date} />{/key}
  {:else if route.name === 'labels'}
    <Labels />
  {:else if route.name === 'methods'}
    <Methods />
  {:else if route.name === 'settings'}
    <Settings />
  {:else if route.name === 'feedback'}
    <Feedback />
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
