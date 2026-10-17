<script lang="ts">
  import type { ExpenseRow } from '../lib/data/models'
  import DayList from '../lib/ui/DayList.svelte'
  import EditExpenseSheet from '../lib/ui/EditExpenseSheet.svelte'
  import TopBar from '../lib/ui/TopBar.svelte'

  let { day }: { day: string } = $props()

  let editing = $state<ExpenseRow | null>(null)
</script>

<TopBar title="Expenses" />
<div class="body">
  <DayList {day} onedit={(row) => (editing = row)} />
</div>

{#if editing}
  <EditExpenseSheet row={editing} onclose={() => (editing = null)} />
{/if}

<style>
  .body {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    padding: 0 16px;
  }
</style>
