<script lang="ts">
  import { dayTotal, rowsForDay } from '../data/expenses'
  import type { ExpenseRow } from '../data/models'
  import { argbToCss } from '../domain/color'
  import { formatHeaderDate } from '../domain/dates'
  import { formatMoney } from '../domain/money'
  import { exportDay } from '../export/exportFiles'
  import { live } from '../live.svelte'
  import ExportButtons from './ExportButtons.svelte'
  import { rowSubtitle, rowTitle } from './rowText'

  let { day, onedit }: { day: string; onedit: (row: ExpenseRow) => void } = $props()

  const rows = live(() => rowsForDay(day), [] as ExpenseRow[])
  const total = live(() => dayTotal(day), 0)
</script>

<div class="list">
  <div class="head">
    <span class="date">{formatHeaderDate(day)}</span>
    <span class="total">{formatMoney(total.value)}</span>
  </div>
  <div class="rows">
    {#if rows.value.length === 0}
      <p class="empty">No expenses</p>
    {:else}
      {#each rows.value as row (row.id)}
        <button class="row" onclick={() => onedit(row)}>
          <span class="dot" style:background={argbToCss(row.paymentMethodColor)}></span>
          <span class="text">
            <span class="title">{rowTitle(row)}</span>
            <span class="sub">{rowSubtitle(row)}</span>
          </span>
          <span class="amount">{formatMoney(row.amountCentavos)}</span>
        </button>
      {/each}
    {/if}
  </div>
  <div class="export-wrap"><ExportButtons produce={(format) => exportDay(day, format)} /></div>
</div>

<style>
  .list {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
  }

  .head {
    display: flex;
    align-items: flex-end;
    padding: 12px 2px;
  }

  .date {
    flex: 1;
    font-size: 13px;
    color: var(--muted);
  }

  .total {
    font-size: 26px;
    font-weight: 700;
  }

  .rows {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
  }

  .empty {
    color: var(--muted);
    padding: 8px 2px;
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

  .dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    flex: none;
  }

  .text {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
  }

  .title,
  .sub {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
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

  .export-wrap {
    padding: 16px 0;
  }
</style>
