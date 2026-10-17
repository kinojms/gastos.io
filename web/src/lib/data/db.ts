import { openDB, type DBSchema, type IDBPDatabase } from 'idb'
import { DEFAULT_LABELS, DEFAULT_PAYMENT_METHODS, type Expense, type Label, type PaymentMethod } from './models'

export interface GastosSchema extends DBSchema {
  labels: { key: number; value: Label }
  paymentMethods: { key: number; value: PaymentMethod }
  expenses: { key: number; value: Expense; indexes: { timestamp: number } }
  prefs: { key: string; value: unknown }
}

export type GastosDb = IDBPDatabase<GastosSchema>

let current: GastosDb | null = null

/** Opens (and on first run seeds) the database, and makes it the one the repositories use. */
export async function openGastosDb(name = 'gastos'): Promise<GastosDb> {
  const db = await openDB<GastosSchema>(name, 1, {
    upgrade(db, _old, _new, tx) {
      db.createObjectStore('labels', { keyPath: 'id', autoIncrement: true })
      db.createObjectStore('paymentMethods', { keyPath: 'id', autoIncrement: true })
      db.createObjectStore('expenses', { keyPath: 'id', autoIncrement: true }).createIndex('timestamp', 'timestamp')
      db.createObjectStore('prefs')
      for (const l of DEFAULT_LABELS) void tx.objectStore('labels').add(l as Label)
      DEFAULT_PAYMENT_METHODS.forEach((m, position) => {
        void tx.objectStore('paymentMethods').add({ ...m, position, archived: false } as PaymentMethod)
      })
    },
  })
  current = db
  return db
}

export function getDb(): GastosDb {
  if (!current) throw new Error('Database is not open')
  return current
}

/** Replaces every label, payment method and expense in one transaction, keeping the given ids. */
export async function replaceAll(
  db: GastosDb,
  labels: Label[],
  methods: PaymentMethod[],
  expenses: Expense[],
): Promise<void> {
  const tx = db.transaction(['labels', 'paymentMethods', 'expenses'], 'readwrite')
  await Promise.all([
    tx.objectStore('labels').clear(),
    tx.objectStore('paymentMethods').clear(),
    tx.objectStore('expenses').clear(),
  ])
  for (const l of labels) void tx.objectStore('labels').put(l)
  for (const m of methods) void tx.objectStore('paymentMethods').put(m)
  for (const e of expenses) void tx.objectStore('expenses').put(e)
  await tx.done
}
