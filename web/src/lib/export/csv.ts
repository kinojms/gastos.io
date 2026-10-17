import { formatTime24 } from '../domain/dates'
import { plainMoney } from '../domain/money'
import { dayOf } from '../domain/dates'
import type { ExpenseRow } from '../data/models'

const BOM = new Uint8Array([0xef, 0xbb, 0xbf])

function quote(field: string): string {
  return /[",\n\r]/.test(field) ? '"' + field.replace(/"/g, '""') + '"' : field
}

/** UTF-8 with BOM. Header, one line per row, then a Total line. Lines are joined with "\n". */
export function writeCsv(rows: ExpenseRow[]): Uint8Array {
  const lines: string[] = ['Date,Time,Label,Payment,Description,Amount,Notes']
  for (const r of rows) {
    lines.push(
      [
        dayOf(r.timestamp),
        formatTime24(r.timestamp),
        r.labelName,
        r.paymentMethodName,
        r.description ?? '',
        plainMoney(r.amountCentavos),
        r.notes ?? '',
      ]
        .map(quote)
        .join(','),
    )
  }
  lines.push(`,,,,Total,${plainMoney(rows.reduce((s, r) => s + r.amountCentavos, 0))},`)
  const body = new TextEncoder().encode(lines.join('\n'))
  const out = new Uint8Array(BOM.length + body.length)
  out.set(BOM, 0)
  out.set(body, BOM.length)
  return out
}
