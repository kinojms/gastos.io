import { rowsForDay, rowsForMonth } from '../data/expenses'
import type { ExpenseRow } from '../data/models'
import { dayOf, formatTime24 } from '../domain/dates'
import { writeCsv } from './csv'
import { writeXlsx, type Cell, type Sheet } from './xlsx'

export type ExportFormat = 'csv' | 'xlsx'

export interface ExportFile {
  name: string
  mime: string
  bytes: Uint8Array
}

export const MIME = {
  csv: 'text/csv',
  xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  json: 'application/json',
} as const

export const dayFileName = (day: string, ext: ExportFormat) => `expenses-${day}.${ext}`
export const monthFileName = (month: string, ext: ExportFormat) => `expenses-${month}.${ext}`

const optional = (s: string | null): Cell => (s ? { text: s } : 'blank')

function expensesSheet(rows: ExpenseRow[]): Sheet {
  return {
    name: 'Expenses',
    header: ['Date', 'Time', 'Label', 'Payment', 'Description', 'Amount', 'Notes'],
    rows: rows.map((r) => [
      { date: dayOf(r.timestamp) },
      { text: formatTime24(r.timestamp) },
      { text: r.labelName },
      { text: r.paymentMethodName },
      optional(r.description),
      { peso: r.amountCentavos },
      optional(r.notes),
    ]),
    totalLabelCol: 4, // "Total" under Description, next to the Amount sum
    sumCols: [5], // Amount = column F
    wrapCols: new Set([6]), // Notes
  }
}

/** One row per group (largest first) with its amount, then a Total row. */
function totalsSheet(name: string, groupHeader: string, rows: ExpenseRow[], key: (r: ExpenseRow) => string): Sheet {
  const sums = new Map<string, number>()
  for (const r of rows) sums.set(key(r), (sums.get(key(r)) ?? 0) + r.amountCentavos)
  const totals = [...sums.entries()].sort((a, b) => b[1] - a[1])
  return {
    name,
    header: [groupHeader, 'Amount'],
    rows: totals.map(([group, total]) => [{ text: group }, { peso: total }]),
    totalLabelCol: 0,
    sumCols: [1],
  }
}

/** A day's expenses, chronological like the month export. */
export async function exportDay(day: string, format: ExportFormat): Promise<ExportFile> {
  const rows = (await rowsForDay(day)).sort((a, b) => a.timestamp - b.timestamp || a.id - b.id)
  const bytes = format === 'csv' ? writeCsv(rows) : writeXlsx([expensesSheet(rows)])
  return { name: dayFileName(day, format), mime: MIME[format], bytes }
}

/** A month's expenses. The XLSX adds "By label" and "By payment" sheets. */
export async function exportMonth(month: string, format: ExportFormat): Promise<ExportFile> {
  const rows = await rowsForMonth(month)
  const bytes =
    format === 'csv'
      ? writeCsv(rows)
      : writeXlsx([
          expensesSheet(rows),
          totalsSheet('By label', 'Label', rows, (r) => r.labelName),
          totalsSheet('By payment', 'Payment', rows, (r) => r.paymentMethodName),
        ])
  return { name: monthFileName(month, format), mime: MIME[format], bytes }
}
