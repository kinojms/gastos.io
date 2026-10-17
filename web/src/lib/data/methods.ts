import { getDb } from './db'
import { emitDataChanged } from './events'
import type { PaymentMethod } from './models'

/** Every method, archived ones included, by position. */
export async function allMethods(): Promise<PaymentMethod[]> {
  const methods = await getDb().getAll('paymentMethods')
  return methods.sort((a, b) => a.position - b.position)
}

/** Methods shown in swipes and pickers, by position. */
export async function activeMethods(): Promise<PaymentMethod[]> {
  return (await allMethods()).filter((m) => !m.archived)
}

/** An error message, or null when the name is acceptable. Names are unique among active methods. */
export function validateMethodName(methods: PaymentMethod[], name: string, excludeId: number | null): string | null {
  const trimmed = name.trim()
  if (!trimmed) return "Name can't be empty"
  const clash = methods.some(
    (m) => !m.archived && m.id !== excludeId && m.name.toLowerCase() === trimmed.toLowerCase(),
  )
  return clash ? 'That payment method already exists' : null
}

export async function addMethod(name: string, colorArgb: number): Promise<number> {
  const methods = await allMethods()
  const position = methods.reduce((m, x) => Math.max(m, x.position), -1) + 1
  const id = (await getDb().add('paymentMethods', {
    name: name.trim(), colorArgb, position, archived: false,
  } as PaymentMethod)) as number
  emitDataChanged()
  return id
}

async function update(id: number, patch: Partial<PaymentMethod>): Promise<void> {
  const tx = getDb().transaction('paymentMethods', 'readwrite')
  const method = await tx.store.get(id)
  if (method) await tx.store.put({ ...method, ...patch })
  await tx.done
  emitDataChanged()
}

export const renameMethod = (id: number, name: string) => update(id, { name: name.trim() })

export const recolorMethod = (id: number, colorArgb: number) => update(id, { colorArgb })

export async function reorderMethods(idsInOrder: number[]): Promise<void> {
  const tx = getDb().transaction('paymentMethods', 'readwrite')
  for (const [position, id] of idsInOrder.entries()) {
    const method = await tx.store.get(id)
    if (method) await tx.store.put({ ...method, position })
  }
  await tx.done
  emitDataChanged()
}

/** Hides the method; its expenses keep it. Throws for the last active method. */
export async function archiveMethod(id: number): Promise<void> {
  const tx = getDb().transaction('paymentMethods', 'readwrite')
  const all = await tx.store.getAll()
  const method = all.find((m) => m.id === id)
  if (!method || method.archived) return
  if (all.filter((m) => !m.archived).length <= 1) {
    throw new Error('At least one payment method must stay active')
  }
  await tx.store.put({ ...method, archived: true })
  await tx.done
  emitDataChanged()
}
