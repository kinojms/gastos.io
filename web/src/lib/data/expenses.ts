import { dayOf, dayRange, monthRange } from '../domain/dates'
import { cleanDescription, cleanNotes } from '../domain/expenseText'
import { getDb } from './db'
import { emitDataChanged } from './events'
import type { DaySummary, Expense, ExpenseRow, Total } from './models'

export interface ExpenseUpdate {
  amountCentavos: number
  labelId: number
  paymentMethodId: number
  timestamp: number
  description: string | null
  notes: string | null
}

export async function addExpense(
  amountCentavos: number,
  labelId: number,
  paymentMethodId: number,
  description?: string | null,
  now: number = Date.now(),
): Promise<number> {
  const id = (await getDb().add('expenses', {
    amountCentavos,
    labelId,
    timestamp: now,
    paymentMethodId,
    description: cleanDescription(description),
    notes: null,
  } as Expense)) as number
  emitDataChanged()
  return id
}

export async function updateExpense(id: number, u: ExpenseUpdate): Promise<void> {
  const tx = getDb().transaction('expenses', 'readwrite')
  const existing = await tx.store.get(id)
  if (existing) {
    await tx.store.put({
      ...existing,
      amountCentavos: u.amountCentavos,
      labelId: u.labelId,
      paymentMethodId: u.paymentMethodId,
      timestamp: u.timestamp,
      description: cleanDescription(u.description),
      notes: cleanNotes(u.notes),
    })
  }
  await tx.done
  emitDataChanged()
}

export async function deleteExpense(id: number): Promise<void> {
  await getDb().delete('expenses', id)
  emitDataChanged()
}

export async function allExpenses(): Promise<Expense[]> {
  const all = await getDb().getAll('expenses')
  return all.sort((a, b) => a.id - b.id)
}

async function inRange(start: number, end: number): Promise<Expense[]> {
  return getDb().getAllFromIndex('expenses', 'timestamp', IDBKeyRange.bound(start, end, false, true))
}

async function toRows(expenses: Expense[]): Promise<ExpenseRow[]> {
  const db = getDb()
  const labels = new Map((await db.getAll('labels')).map((l) => [l.id, l]))
  const methods = new Map((await db.getAll('paymentMethods')).map((m) => [m.id, m]))
  return expenses.map((e) => ({
    ...e,
    labelName: labels.get(e.labelId)?.name ?? '',
    labelColor: labels.get(e.labelId)?.colorArgb ?? 0,
    paymentMethodName: methods.get(e.paymentMethodId)?.name ?? '',
    paymentMethodColor: methods.get(e.paymentMethodId)?.colorArgb ?? 0,
  }))
}

/** A day's rows, newest first. */
export async function rowsForDay(day: string): Promise<ExpenseRow[]> {
  const [s, e] = dayRange(day)
  const list = await inRange(s, e)
  list.sort((a, b) => b.timestamp - a.timestamp || b.id - a.id)
  return toRows(list)
}

/** A month's rows, oldest first (for export). */
export async function rowsForMonth(month: string): Promise<ExpenseRow[]> {
  const [s, e] = monthRange(month)
  const list = await inRange(s, e)
  list.sort((a, b) => a.timestamp - b.timestamp || a.id - b.id)
  return toRows(list)
}

export async function dayTotal(day: string): Promise<number> {
  const [s, e] = dayRange(day)
  return (await inRange(s, e)).reduce((sum, x) => sum + x.amountCentavos, 0)
}

/** Days of the month that have expenses, newest day first. */
export async function daySummaries(month: string): Promise<DaySummary[]> {
  const [s, e] = monthRange(month)
  const byDay = new Map<string, DaySummary>()
  for (const x of await inRange(s, e)) {
    const date = dayOf(x.timestamp)
    const row = byDay.get(date) ?? { date, count: 0, totalCentavos: 0 }
    row.count += 1
    row.totalCentavos += x.amountCentavos
    byDay.set(date, row)
  }
  return [...byDay.values()].sort((a, b) => (a.date < b.date ? 1 : -1))
}

async function totals(
  month: string,
  key: (e: Expense) => number,
  source: 'labels' | 'paymentMethods',
): Promise<Total[]> {
  const [s, e] = monthRange(month)
  const items = new Map((await getDb().getAll(source)).map((x) => [x.id, x]))
  const sums = new Map<number, number>()
  for (const x of await inRange(s, e)) sums.set(key(x), (sums.get(key(x)) ?? 0) + x.amountCentavos)
  return [...sums.entries()]
    .flatMap(([id, totalCentavos]) => {
      const item = items.get(id)
      return item ? [{ id, name: item.name, colorArgb: item.colorArgb, totalCentavos, position: item.position }] : []
    })
    .sort((a, b) => b.totalCentavos - a.totalCentavos || a.position - b.position)
    .map(({ position: _p, ...rest }) => rest)
}

/** Month totals per label, largest first. */
export const monthByLabel = (month: string) => totals(month, (e) => e.labelId, 'labels')

/** Month totals per payment method, largest first; archived methods included. */
export const monthByMethod = (month: string) => totals(month, (e) => e.paymentMethodId, 'paymentMethods')

export async function countByLabel(labelId: number): Promise<number> {
  return (await getDb().getAll('expenses')).filter((e) => e.labelId === labelId).length
}

/** Timestamp of the oldest expense, or null when there are none. */
export async function oldestTimestamp(): Promise<number | null> {
  const cursor = await getDb().transaction('expenses').store.index('timestamp').openCursor()
  return cursor ? cursor.key : null
}
