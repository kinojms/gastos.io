<script lang="ts">
  import { onMount } from 'svelte'
  import { daySummaries } from '../lib/data/expenses'
  import type { DaySummary, ExpenseRow } from '../lib/data/models'
  import { addMonths, formatDayRow, monthOf, today as todayOf } from '../lib/domain/dates'
  import { formatMoney } from '../lib/domain/money'
  import { live } from '../lib/live.svelte'
  import { navigate, router, type Tab } from '../lib/router.svelte'
  import DayList from '../lib/ui/DayList.svelte'
  import EditExpenseSheet from '../lib/ui/EditExpenseSheet.svelte'
  import MonthSwitcher from '../lib/ui/MonthSwitcher.svelte'
  import MonthTab from '../lib/ui/MonthTab.svelte'
  import TopBar from '../lib/ui/TopBar.svelte'

  const tabs: { id: Tab; name: string }[] = [
    { id: 'today', name: 'Today' },
    { id: 'days', name: 'Days' },
    { id: 'month', name: 'Month' },
  ]

  let tab = $state<Tab>(router.route.name === 'expenses' ? (router.route.tab ?? 'today') : 'today')
  let today = $state(todayOf())
  let month = $state(monthOf(todayOf()))
  let editing = $state<ExpenseRow | null>(null)

  const days = live(() => daySummaries(month), [] as DaySummary[])

  onMount(() => {
    const timer = setInterval(() => (today = todayOf()), 15_000)
    return () => clearInterval(timer)
  })

  function pick(id: Tab) {
    tab = id
    history.replaceState(history.state, '', `#/expenses?tab=${id}`)
  }

  const prev = () => (month = addMonths(month, -1))
  const next = () => (month = addMonths(month, 1))
</script>

<TopBar title="Expenses" />
<div class="tabs" role="tablist">
  {#each tabs as t}
    <button role="tab" aria-selected={tab === t.id} class:on={tab === t.id} onclick={() => pick(t.id)}>{t.name}</button>
  {/each}
</div>

<div class="body">
  {#if tab === 'today'}
    <DayList day={today} onedit={(row) => (editing = row)} />
  {:else if tab === 'days'}
    <div class="days">
      <MonthSwitcher {month} onprev={prev} onnext={next} />
      {#if days.value.length === 0}<p class="empty">No expenses this month</p>{/if}
      <div class="rows">
        {#each days.value as d (d.date)}
          <button class="row" onclick={() => navigate(`/expenses/day/${d.date}`)}>
            <span class="text">
              <span class="title">{formatDayRow(d.date)}</span>
              <span class="sub">{d.count === 1 ? '1 expense' : `${d.count} expenses`}</span>
            </span>
            <span class="amount">{formatMoney(d.totalCentavos)}</span>
          </button>
        {/each}
      </div>
    </div>
  {:else}
    <MonthTab {month} onprev={prev} onnext={next} />
  {/if}
</div>

{#if editing}
  <EditExpenseSheet row={editing} onclose={() => (editing = null)} />
{/if}

<style>
  .tabs {
    display: flex;
    margin: 0 16px;
    padding: 4px;
    border-radius: 12px;
    background: var(--surface2);
  }

  .tabs button {
    flex: 1;
    border: 0;
    border-radius: 9px;
    background: none;
    padding: 7px 0;
    font-size: 13px;
    font-weight: 600;
    color: var(--muted);
  }

  .tabs button.on {
    background: var(--surface);
    color: var(--fg);
  }

  .body {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    padding: 0 16px;
  }

  .days {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
  }

  .empty {
    color: var(--muted);
    padding: 18px 2px;
  }

  .rows {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
  }

  .row {
    display: flex;
    align-items: center;
    gap: 10px;
    width: 100%;
    padding: 12px 2px;
    border: 0;
    border-bottom: 1px solid var(--line);
    background: none;
    text-align: left;
  }

  .text {
    flex: 1;
    display: flex;
    flex-direction: column;
  }

  .title {
    font-size: 15px;
  }

  .sub {
    font-size: 12px;
    color: var(--muted);
  }

  .amount {
    font-weight: 600;
  }
</style>
