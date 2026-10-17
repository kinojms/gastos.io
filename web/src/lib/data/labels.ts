import { getDb } from './db'
import { emitDataChanged } from './events'
import type { Label } from './models'

/** All labels, by position. */
export async function allLabels(): Promise<Label[]> {
  const labels = await getDb().getAll('labels')
  return labels.sort((a, b) => a.position - b.position)
}

/** An error message, or null when the name is acceptable. excludeId is the label being edited. */
export function validateLabelName(labels: Label[], name: string, excludeId: number | null): string | null {
  const trimmed = name.trim()
  if (!trimmed) return "Name can't be empty"
  const clash = labels.some((l) => l.id !== excludeId && l.name.toLowerCase() === trimmed.toLowerCase())
  return clash ? 'That label already exists' : null
}

export async function addLabel(name: string, colorArgb: number): Promise<number> {
  const db = getDb()
  const labels = await allLabels()
  const position = labels.reduce((m, l) => Math.max(m, l.position), -1) + 1
  const id = (await db.add('labels', { name: name.trim(), colorArgb, position, isOther: false } as Label)) as number
  emitDataChanged()
  return id
}

async function update(id: number, patch: Partial<Label>): Promise<void> {
  const db = getDb()
  const tx = db.transaction('labels', 'readwrite')
  const label = await tx.store.get(id)
  if (label) await tx.store.put({ ...label, ...patch })
  await tx.done
  emitDataChanged()
}

export const renameLabel = (id: number, name: string) => update(id, { name: name.trim() })

export const recolorLabel = (id: number, colorArgb: number) => update(id, { colorArgb })

export async function reorderLabels(idsInOrder: number[]): Promise<void> {
  const tx = getDb().transaction('labels', 'readwrite')
  for (const [position, id] of idsInOrder.entries()) {
    const label = await tx.store.get(id)
    if (label) await tx.store.put({ ...label, position })
  }
  await tx.done
  emitDataChanged()
}

/** Moves the label's expenses to Other, then deletes it. Other itself can't be deleted. */
export async function deleteLabel(id: number): Promise<void> {
  const tx = getDb().transaction(['labels', 'expenses'], 'readwrite')
  const label = await tx.objectStore('labels').get(id)
  if (!label) return
  if (label.isOther) throw new Error('The Other label cannot be deleted')
  const all = await tx.objectStore('labels').getAll()
  const other = all.find((l) => l.isOther)
  if (!other) throw new Error('Other label missing')
  let cursor = await tx.objectStore('expenses').openCursor()
  while (cursor) {
    if (cursor.value.labelId === id) await cursor.update({ ...cursor.value, labelId: other.id })
    cursor = await cursor.continue()
  }
  await tx.objectStore('labels').delete(id)
  await tx.done
  emitDataChanged()
}
