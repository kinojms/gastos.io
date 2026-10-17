import type { ExpenseRow } from '../data/models'
import { formatTime } from '../domain/dates'

/** The description (when typed) is the title and pushes the label into the subtitle. */
export function rowTitle(row: ExpenseRow): string {
  return row.description ?? row.labelName
}

export function rowSubtitle(row: ExpenseRow): string {
  const time = formatTime(row.timestamp)
  return row.description !== null
    ? `${row.labelName} · ${row.paymentMethodName} · ${time}`
    : `${row.paymentMethodName} · ${time}`
}
