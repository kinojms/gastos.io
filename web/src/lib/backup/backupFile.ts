import { getDb, replaceAll } from '../data/db'
import { emitDataChanged } from '../data/events'
import { allExpenses } from '../data/expenses'
import { allLabels } from '../data/labels'
import { allMethods } from '../data/methods'
import { DEFAULT_PAYMENT_METHODS, type Expense, type Label, type PaymentMethod } from '../data/models'

export const CURRENT_SCHEMA = 2
export const DAMAGED = "This backup file is damaged or isn't a Gastos.io backup."
export const NEWER = 'This backup is from a newer version of Gastos.io. Update the app first.'

export class BackupError extends Error {}

/** Field names exactly as Android's kotlinx.serialization writes them. */
export interface BackupFile {
  schemaVersion: number
  exportedAt: string
  labels: { id: number; name: string; colorArgb: number; position: number; isOther: boolean }[]
  expenses: {
    id: number
    amountCentavos: number
    labelId: number
    timestamp: number
    paymentMethodId: number | null
    description: string | null
    notes: string | null
  }[]
  paymentMethods: { id: number; name: string; colorArgb: number; position: number; archived: boolean }[]
}

export interface ParsedBackup {
  labels: Label[]
  methods: PaymentMethod[]
  expenses: Expense[]
  exportedAt: string
}

export function buildBackup(labels: Label[], methods: PaymentMethod[], expenses: Expense[], now: Date): BackupFile {
  return {
    schemaVersion: CURRENT_SCHEMA,
    exportedAt: now.toISOString().replace(/\.\d{3}Z$/, 'Z'),
    labels: labels.map((l) => ({
      id: l.id, name: l.name, colorArgb: l.colorArgb, position: l.position, isOther: l.isOther,
    })),
    expenses: expenses.map((e) => ({
      id: e.id,
      amountCentavos: e.amountCentavos,
      labelId: e.labelId,
      timestamp: e.timestamp,
      paymentMethodId: e.paymentMethodId,
      description: e.description,
      notes: e.notes,
    })),
    paymentMethods: methods.map((m) => ({
      id: m.id, name: m.name, colorArgb: m.colorArgb, position: m.position, archived: m.archived,
    })),
  }
}

export function encodeBackup(f: BackupFile): string {
  return JSON.stringify(f)
}

const isInt = (v: unknown): v is number => typeof v === 'number' && Number.isInteger(v)
const isStr = (v: unknown): v is string => typeof v === 'string'
const strOrNull = (v: unknown): string | null => (v === undefined || v === null ? null : isStr(v) ? v : fail())

function fail(): never {
  throw new BackupError(DAMAGED)
}

function list(v: unknown): Record<string, unknown>[] {
  if (v === undefined) return []
  if (!Array.isArray(v) || v.some((x) => typeof x !== 'object' || x === null)) fail()
  return v as Record<string, unknown>[]
}

/** Decodes and validates backup text. Throws [BackupError] with a user-facing message. */
export function parseBackup(text: string): ParsedBackup {
  let root: unknown
  try {
    root = JSON.parse(text)
  } catch {
    fail()
  }
  if (typeof root !== 'object' || root === null || Array.isArray(root)) fail()
  const o = root as Record<string, unknown>
  if (!isInt(o.schemaVersion)) fail()
  if (o.schemaVersion > CURRENT_SCHEMA) throw new BackupError(NEWER)
  if (!isStr(o.exportedAt)) fail()

  const labels: Label[] = list(o.labels).map((l) => {
    if (!isInt(l.id) || !isStr(l.name) || !isInt(l.colorArgb) || !isInt(l.position)) fail()
    return { id: l.id, name: l.name, colorArgb: l.colorArgb, position: l.position, isOther: l.isOther === true }
  })
  const rawMethods = list(o.paymentMethods)
  const schema1 = rawMethods.length === 0

  let methods: PaymentMethod[]
  if (schema1) {
    // Schema 1 has no payment methods: restore the six defaults (Cash = id 1).
    methods = DEFAULT_PAYMENT_METHODS.map((m, i) => ({ id: i + 1, name: m.name, colorArgb: m.colorArgb, position: i, archived: false }))
  } else {
    methods = rawMethods.map((m) => {
      if (!isInt(m.id) || !isStr(m.name) || !isInt(m.colorArgb) || !isInt(m.position)) fail()
      return { id: m.id, name: m.name, colorArgb: m.colorArgb, position: m.position, archived: m.archived === true }
    })
  }

  const expenses: Expense[] = list(o.expenses).map((e) => {
    if (!isInt(e.id) || !isInt(e.amountCentavos) || !isInt(e.labelId) || !isInt(e.timestamp)) fail()
    const methodId = schema1 ? methods[0].id : e.paymentMethodId
    if (!isInt(methodId)) fail()
    return {
      id: e.id,
      amountCentavos: e.amountCentavos,
      labelId: e.labelId,
      timestamp: e.timestamp,
      paymentMethodId: methodId,
      description: strOrNull(e.description),
      notes: strOrNull(e.notes),
    }
  })

  validate(labels, methods, expenses)
  return { labels, methods, expenses, exportedAt: o.exportedAt }
}

/** Rejects files that would leave the app broken, same rules as Android's BackupManager.validate. */
function validate(labels: Label[], methods: PaymentMethod[], expenses: Expense[]): void {
  const labelIds = new Set(labels.map((l) => l.id))
  const methodIds = new Set(methods.map((m) => m.id))
  const ok =
    labels.filter((l) => l.isOther).length === 1 &&
    labelIds.size === labels.length &&
    [...labelIds].every((id) => id > 0) &&
    methodIds.size === methods.length &&
    [...methodIds].every((id) => id > 0) &&
    methods.some((m) => !m.archived) &&
    new Set(expenses.map((e) => e.id)).size === expenses.length &&
    expenses.every((e) => e.id > 0 && labelIds.has(e.labelId) && methodIds.has(e.paymentMethodId))
  if (!ok) fail()
}

/** What a file holds, shown before the user confirms replacing local data with it. */
export function previewJson(text: string): { expenseCount: number; exportedAt: string } {
  const p = parseBackup(text)
  return { expenseCount: p.expenses.length, exportedAt: p.exportedAt }
}

/** All local data as backup JSON (schema 2). No network. */
export async function exportJson(): Promise<string> {
  const [labels, methods, expenses] = await Promise.all([allLabels(), allMethods(), allExpenses()])
  return encodeBackup(buildBackup(labels, methods, expenses, new Date()))
}

/** Replaces all local data with the backup text. Validates first, so a bad file changes nothing. */
export async function restoreJson(text: string): Promise<{ expenseCount: number }> {
  const p = parseBackup(text)
  await replaceAll(getDb(), p.labels, p.methods, p.expenses)
  emitDataChanged()
  return { expenseCount: p.expenses.length }
}
