<script lang="ts">
  import { dayTotal, deleteExpense, rowsForDay } from '../data/expenses'
  import { prefsStore, setPref } from '../data/prefs'
  import { confirmCopy } from '../domain/confirmCopy'
  import { guide } from '../guide/guide.svelte'
  import ConfirmDialog from './ConfirmDialog.svelte'
  import { closeOpenRow } from './openRow.svelte'
  import SwipeRow from './SwipeRow.svelte'
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

  let deleting = $state<ExpenseRow | null>(null)

  /** The first row peeks open once per phone to show that rows can be swiped. */
  const peekId = $derived(
    rows.value.length > 0 && $prefsStore.swipe_hint_seen !== true && guide.step === null ? rows.value[0].id : null,
  )

  $effect(() => closeOpenRow)

  async function confirmDelete() {
    const row = deleting
    deleting = null
    if (row) await deleteExpense(row.id)
  }
</script>

<div class="list">
  <div class="head">
    <span class="date">{formatHeaderDate(day)}</span>
    <span class="total">{formatMoney(total.value)}</span>
  </div>
  <div class="rows" onscroll={closeOpenRow}>
    {#if rows.value.length === 0}
      <p class="empty">No expenses</p>
    {:else}
      {#each rows.value as row (row.id)}
        <SwipeRow
          id={row.id}
          onedit={() => onedit(row)}
          ondelete={() => (deleting = row)}
          peek={row.id === peekId}
          onpeekstart={() => void setPref('swipe_hint_seen', true)}
        >
        <button class="row" onclick={() => onedit(row)}>
          <span class="dot" style:background={argbToCss(row.paymentMethodColor)}></span>
          <span class="text">
            <span class="title">{rowTitle(row)}</span>
            <span class="sub">{rowSubtitle(row)}</span>
          </span>
          <span class="amount">{formatMoney(row.amountCentavos)}</span>
        </button>
        </SwipeRow>
      {/each}
    {/if}
  </div>
  <div class="export-wrap"><ExportButtons produce={(format) => exportDay(day, format)} /></div>
</div>

<ConfirmDialog
  open={deleting !== null}
  message={deleting ? confirmCopy.deleteExpense(deleting.amountCentavos, deleting.labelName) : ''}
  confirmText="Delete"
  danger
  onConfirm={confirmDelete}
  onCancel={() => (deleting = null)}
/>

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
