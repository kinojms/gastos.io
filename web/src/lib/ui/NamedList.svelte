<script lang="ts" module>
  export interface NamedItem {
    id: number
    name: string
    colorArgb: number
  }

  /** What differs between the Labels and Payment methods screens. */
  export interface NamedListConfig<T extends NamedItem> {
    title: string
    addButton: string
    addTitle: string
    editTitle: string
    deleteText: string
    palette: number[]
    load: () => Promise<T[]>
    validate: (items: T[], name: string, excludeId: number | null) => string | null
    canDelete: (item: T, items: T[]) => boolean
    /** Confirm copy, exactly as in Android's ConfirmCopy. */
    copy: {
      add: (name: string) => string
      rename: (old: string, next: string) => string
      recolor: (name: string) => string
      reorder: () => string
      delete: (item: T, count: number) => string
    }
    /** How many expenses a delete affects (labels move them to Other). */
    countFor: (item: T) => Promise<number>
    ops: {
      add: (name: string, color: number) => Promise<unknown>
      rename: (id: number, name: string) => Promise<unknown>
      recolor: (id: number, color: number) => Promise<unknown>
      reorder: (ids: number[]) => Promise<unknown>
      delete: (id: number) => Promise<unknown>
    }
  }
</script>

<script lang="ts" generics="T extends NamedItem">
  import { argbToCss } from '../domain/color'
  import { live } from '../live.svelte'
  import ConfirmDialog from './ConfirmDialog.svelte'
  import EditItemDialog from './EditItemDialog.svelte'
  import ReorderList from './ReorderList.svelte'
  import { showToast } from './toastState.svelte'
  import TopBar from './TopBar.svelte'

  let { cfg }: { cfg: NamedListConfig<T> } = $props()

  interface Pending {
    message: string
    confirmText: string
    danger?: boolean
    run: () => Promise<unknown>
  }

  const stored = live(() => cfg.load(), [] as T[])
  let localOrder = $state<number[] | null>(null)
  let editing = $state<T | null>(null)
  let adding = $state(false)
  let queue = $state<Pending[]>([])

  const displayed = $derived.by(() => {
    const items = stored.value
    if (!localOrder) return items
    const byId = new Map(items.map((i) => [i.id, i]))
    const ordered = localOrder.flatMap((id) => (byId.has(id) ? [byId.get(id)!] : []))
    const missing = items.filter((i) => !localOrder!.includes(i.id))
    return [...ordered, ...missing]
  })
  const orderChanged = $derived(displayed.some((item, i) => item.id !== stored.value[i]?.id))

  function move(from: number, to: number) {
    const ids = displayed.map((i) => i.id)
    ids.splice(to, 0, ids.splice(from, 1)[0])
    localOrder = ids
  }

  async function write(op: () => Promise<unknown>) {
    try {
      await op()
    } catch (e) {
      showToast(e instanceof Error && e.message ? e.message : "Couldn't save. Try again.")
    }
  }

  function confirmNext() {
    const [head, ...rest] = queue
    queue = rest
    void write(async () => {
      await head.run()
      if (head.message === cfg.copy.reorder()) localOrder = null
    })
  }

  function saveAdd(name: string, color: number) {
    adding = false
    queue = [{ message: cfg.copy.add(name), confirmText: 'Add', run: () => cfg.ops.add(name, color) }]
  }

  function saveEdit(item: T, name: string, color: number) {
    editing = null
    const next: Pending[] = []
    if (name !== item.name) {
      next.push({ message: cfg.copy.rename(item.name, name), confirmText: 'Rename', run: () => cfg.ops.rename(item.id, name) })
    }
    if (color !== item.colorArgb) {
      next.push({ message: cfg.copy.recolor(name), confirmText: 'Change', run: () => cfg.ops.recolor(item.id, color) })
    }
    queue = next
  }

  async function askDelete(item: T) {
    editing = null
    const count = await cfg.countFor(item)
    queue = [
      { message: cfg.copy.delete(item, count), confirmText: 'Delete', danger: true, run: () => cfg.ops.delete(item.id) },
    ]
  }
</script>

<TopBar title={cfg.title}>
  {#snippet actions()}
    {#if orderChanged}
      <button
        class="save-order"
        onclick={() => (queue = [{ message: cfg.copy.reorder(), confirmText: 'Save', run: () => cfg.ops.reorder(displayed.map((i) => i.id)) }])}
      >Save order</button>
    {/if}
  {/snippet}
</TopBar>

<div class="body">
  <ReorderList items={displayed} onmove={move}>
    {#snippet row(item)}
      <button class="row" onclick={() => (editing = item)}>
        <span class="dot" style:background={argbToCss(item.colorArgb)}>{item.name.slice(0, 1).toUpperCase()}</span>
        <span class="name">{item.name}</span>
        <span class="edit">Edit</span>
      </button>
    {/snippet}
  </ReorderList>
</div>
<div class="add-wrap"><button class="add" onclick={() => (adding = true)}>{cfg.addButton}</button></div>

{#if adding}
  <EditItemDialog
    title={cfg.addTitle}
    initialName=""
    initialColor={cfg.palette[0]}
    palette={cfg.palette}
    validate={(name) => cfg.validate(stored.value, name, null)}
    onsave={saveAdd}
    onclose={() => (adding = false)}
  />
{/if}

{#if editing}
  {@const item = editing}
  <EditItemDialog
    title={cfg.editTitle}
    initialName={item.name}
    initialColor={item.colorArgb}
    palette={cfg.palette}
    validate={(name) => cfg.validate(stored.value, name, item.id)}
    deleteText={cfg.canDelete(item, stored.value) ? cfg.deleteText : undefined}
    onsave={(name, color) => saveEdit(item, name, color)}
    ondelete={() => void askDelete(item)}
    onclose={() => (editing = null)}
  />
{/if}

<ConfirmDialog
  open={queue.length > 0}
  message={queue[0]?.message ?? ''}
  confirmText={queue[0]?.confirmText ?? 'Confirm'}
  danger={queue[0]?.danger ?? false}
  onConfirm={confirmNext}
  onCancel={() => (queue = [])}
/>

<style>
  .body {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    padding: 0 16px;
  }

  .row {
    flex: 1;
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px 0;
    border: 0;
    background: none;
    text-align: left;
  }

  .dot {
    width: 30px;
    height: 30px;
    border-radius: 50%;
    display: grid;
    place-items: center;
    color: #fff;
    font-size: 13px;
    font-weight: 700;
    flex: none;
  }

  .name {
    flex: 1;
    font-weight: 600;
  }

  .edit {
    font-size: 13px;
    color: var(--muted);
  }

  .save-order {
    border: 0;
    background: none;
    padding: 10px;
    color: var(--accent);
    font-weight: 600;
  }

  .add-wrap {
    padding: 16px;
  }

  .add {
    width: 100%;
    height: 48px;
    border: 0;
    border-radius: 12px;
    background: var(--surface2);
    font-weight: 600;
  }
</style>
