<script lang="ts">
  import { onMount } from 'svelte'
  import { deleteExpense, updateExpense } from '../data/expenses'
  import { allLabels } from '../data/labels'
  import { allMethods } from '../data/methods'
  import type { ExpenseRow, Label, PaymentMethod } from '../data/models'
  import { applyKey, toCentavos } from '../domain/amountInput'
  import { combineDayTime, dayOf, formatTime24 } from '../domain/dates'
  import { confirmCopy } from '../domain/confirmCopy'
  import { DESCRIPTION_MAX, NOTES_MAX, cleanDescription, cleanNotes } from '../domain/expenseText'
  import { plainMoney } from '../domain/money'
  import ConfirmDialog from './ConfirmDialog.svelte'
  import Sheet from './Sheet.svelte'
  import VoiceButton from './VoiceButton.svelte'

  let { row, onclose }: { row: ExpenseRow; onclose: () => void } = $props()

  /** Rebuilds raw text through the numpad rules so typed or pasted text obeys them too. */
  function sanitize(raw: string): string {
    let out = ''
    for (const ch of raw) {
      if (ch >= '0' && ch <= '9') out = applyKey(out, { digit: Number(ch) })
      else if (ch === '.') out = applyKey(out, 'dot')
    }
    return out
  }

  // The form edits a copy of the row as it was when the sheet opened.
  /* svelte-ignore state_referenced_locally */
  let amount = $state(plainMoney(row.amountCentavos))
  /* svelte-ignore state_referenced_locally */
  let labelId = $state(row.labelId)
  /* svelte-ignore state_referenced_locally */
  let methodId = $state(row.paymentMethodId)
  /* svelte-ignore state_referenced_locally */
  let description = $state(row.description ?? '')
  /* svelte-ignore state_referenced_locally */
  let notes = $state(row.notes ?? '')
  /* svelte-ignore state_referenced_locally */
  let date = $state(dayOf(row.timestamp))
  /* svelte-ignore state_referenced_locally */
  let time = $state(formatTime24(row.timestamp))
  let labels = $state<Label[]>([])
  let methods = $state<PaymentMethod[]>([])
  let confirm = $state<'save' | 'delete' | null>(null)

  onMount(async () => {
    labels = await allLabels()
    methods = (await allMethods()).filter((m) => !m.archived || m.id === row.paymentMethodId)
  })

  const cents = $derived(toCentavos(amount))
  const labelName = $derived(labels.find((l) => l.id === labelId)?.name ?? row.labelName)

  async function save() {
    if (cents === null) return
    confirm = null
    await updateExpense(row.id, {
      amountCentavos: cents,
      labelId,
      paymentMethodId: methodId,
      timestamp: combineDayTime(date, time),
      description: cleanDescription(description),
      notes: cleanNotes(notes),
    })
    onclose()
  }

  async function remove() {
    confirm = null
    await deleteExpense(row.id)
    onclose()
  }
</script>

<Sheet open={true} onClose={onclose}>
  <div class="form">
    <h2>Edit expense</h2>

    <label>
      <span>Amount (&#8369;)</span>
      <input
        type="text"
        inputmode="decimal"
        value={amount}
        oninput={(e) => {
          amount = sanitize(e.currentTarget.value)
          e.currentTarget.value = amount
        }}
      />
    </label>

    <label>
      <span>Label</span>
      <select bind:value={labelId}>
        {#each labels as l (l.id)}<option value={l.id}>{l.name}</option>{/each}
        {#if labels.length === 0}<option value={row.labelId}>{row.labelName}</option>{/if}
      </select>
    </label>

    <label>
      <span>What did you buy?</span>
      <div class="with-mic">
        <input type="text" maxlength={DESCRIPTION_MAX} bind:value={description} />
        <div class="mic"><VoiceButton onresult={(text) => (description = text)} /></div>
      </div>
    </label>

    <label>
      <span>Payment</span>
      <select bind:value={methodId}>
        {#each methods as m (m.id)}<option value={m.id}>{m.name}</option>{/each}
        {#if methods.length === 0}<option value={row.paymentMethodId}>{row.paymentMethodName}</option>{/if}
      </select>
    </label>

    <label>
      <span>Notes</span>
      <textarea rows="4" maxlength={NOTES_MAX} placeholder="Add notes…" bind:value={notes}></textarea>
    </label>

    <div class="when">
      <input type="date" aria-label="Date" bind:value={date} />
      <input type="time" aria-label="Time" bind:value={time} />
    </div>

    <div class="buttons">
      <button class="delete" onclick={() => (confirm = 'delete')}>Delete</button>
      <button class="save" disabled={cents === null} onclick={() => (confirm = 'save')}>Save</button>
    </div>
  </div>
</Sheet>

<ConfirmDialog
  open={confirm === 'save' && cents !== null}
  message={confirmCopy.saveExpense(cents ?? 0, labelName)}
  confirmText="Save"
  onConfirm={save}
  onCancel={() => (confirm = null)}
/>
<ConfirmDialog
  open={confirm === 'delete'}
  message={confirmCopy.deleteExpense(row.amountCentavos, row.labelName)}
  confirmText="Delete"
  danger
  onConfirm={remove}
  onCancel={() => (confirm = null)}
/>

<style>
  .form {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  h2 {
    margin: 0;
    font-size: 18px;
  }

  label {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  label > span {
    font-size: 12px;
    color: var(--muted);
  }

  input,
  select,
  textarea {
    width: 100%;
    min-height: 48px;
    padding: 10px 12px;
    border: 1px solid var(--line);
    border-radius: 12px;
    background: var(--bg);
    color: var(--fg);
  }

  textarea {
    resize: vertical;
  }

  .with-mic {
    position: relative;
  }

  .with-mic input {
    padding-right: 52px;
  }

  .mic {
    position: absolute;
    right: 2px;
    top: 2px;
  }

  .when {
    display: flex;
    gap: 8px;
  }

  .buttons {
    display: flex;
    gap: 8px;
    padding-bottom: 8px;
  }

  .buttons button {
    flex: 1;
    height: 48px;
    border-radius: 12px;
    font-weight: 700;
  }

  .delete {
    border: 1px solid var(--danger);
    background: none;
    color: var(--danger);
  }

  .save {
    border: 0;
    background: var(--accent);
    color: var(--on-accent);
  }

  .save:disabled {
    opacity: 0.5;
  }
</style>
