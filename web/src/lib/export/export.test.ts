import { strFromU8, unzipSync } from 'fflate'
import { beforeEach, describe, expect, it } from 'vitest'
import { openGastosDb } from '../data/db'
import { addExpense, updateExpense } from '../data/expenses'
import { allLabels } from '../data/labels'
import { allMethods } from '../data/methods'
import type { ExpenseRow } from '../data/models'
import { writeCsv } from './csv'
import { dayFileName, exportDay, exportMonth, monthFileName } from './exportFiles'
import { autoWidths, writeXlsx, type Sheet } from './xlsx'

const at = (hhmm: string) => Date.parse(`2026-10-16T${hhmm}:00+08:00`)

function row(label: string, centavos: number, hhmm = '08:10', o: Partial<ExpenseRow> = {}): ExpenseRow {
  return {
    id: 1, amountCentavos: centavos, labelId: 1, timestamp: at(hhmm), paymentMethodId: 1,
    description: null, notes: null, labelName: label, labelColor: 0, paymentMethodName: 'Cash', paymentMethodColor: 0,
    ...o,
  }
}

const text = (bytes: Uint8Array) => new TextDecoder().decode(bytes.slice(3))

describe('csv', () => {
  it('writes a BOM, the Android header, rows and a total line', () => {
    const bytes = writeCsv([
      row('Food', 18_500, '12:30', { paymentMethodName: 'GCash', description: 'Jollibee' }),
      row('Transpo', 30_000, '13:00'),
    ])
    expect([...bytes.slice(0, 3)]).toEqual([0xef, 0xbb, 0xbf])
    expect(text(bytes).split('\n')).toEqual([
      'Date,Time,Label,Payment,Description,Amount,Notes',
      '2026-10-16,12:30,Food,GCash,Jollibee,185.00,',
      '2026-10-16,13:00,Transpo,Cash,,300.00,',
      ',,,,Total,485.00,',
    ])
  })

  it('quotes commas, quotes and newlines', () => {
    const out = text(writeCsv([row('Food, "snacks"', 100, '08:10', { notes: 'He said "hi", ok\nbye' })]))
    expect(out).toBe(
      'Date,Time,Label,Payment,Description,Amount,Notes\n' +
        '2026-10-16,08:10,"Food, ""snacks""",Cash,,1.00,"He said ""hi"", ok\nbye"\n' +
        ',,,,Total,1.00,',
    )
  })
})

function sheetOf(bytes: Uint8Array, n: number): string {
  return strFromU8(unzipSync(bytes)[`xl/worksheets/sheet${n}.xml`])
}

describe('xlsx', () => {
  const sheet: Sheet = {
    name: 'Expenses',
    header: ['Date', 'Amount', 'Notes'],
    rows: [
      [{ date: '2026-10-16' }, { peso: 1050 }, { text: 'a <b> & "c"' }],
      [{ date: '2026-10-17' }, { peso: 2000 }, 'blank'],
    ],
    totalLabelCol: 0,
    sumCols: [1],
    wrapCols: new Set([2]),
  }

  it('has the workbook parts, a SUM total, peso format and real dates', () => {
    const files = unzipSync(writeXlsx([sheet]))
    expect(Object.keys(files).sort()).toEqual([
      '[Content_Types].xml', '_rels/.rels', 'xl/_rels/workbook.xml.rels', 'xl/styles.xml', 'xl/workbook.xml',
      'xl/worksheets/sheet1.xml',
    ])
    expect(strFromU8(files['xl/styles.xml'])).toContain('formatCode="&quot;₱&quot;#,##0.00"')
    const xml = strFromU8(files['xl/worksheets/sheet1.xml'])
    expect(xml).toContain('<pane ySplit="1" topLeftCell="A2" state="frozen"/>')
    expect(xml).toContain('<f>SUM(B2:B3)</f><v>30.50</v>')
    expect(xml).toContain('<v>46311</v>') // 2026-10-16 as an Excel serial date
    expect(xml).toContain('<v>10.50</v>')
    expect(xml).toContain('a &lt;b&gt; &amp; &quot;c&quot;')
  })

  it('auto-sizes columns and caps wrapped ones at 40', () => {
    const w = autoWidths({ ...sheet, rows: [[{ date: '2026-10-16' }, { peso: 1050 }, { text: 'x'.repeat(200) }]] })
    expect(w).toEqual([12, 8, 40])
  })
})

let n = 0
beforeEach(async () => {
  await openGastosDb('x' + n++)
})

describe('export files', () => {
  it('names files like Android', () => {
    expect(dayFileName('2026-10-16', 'csv')).toBe('expenses-2026-10-16.csv')
    expect(monthFileName('2026-10', 'xlsx')).toBe('expenses-2026-10.xlsx')
  })

  it('exports a day chronologically and a month with three sheets', async () => {
    const labels = await allLabels()
    const methods = await allMethods()
    await addExpense(18_500, labels[0].id, methods[1].id, 'Jollibee', at('12:30'))
    const id = await addExpense(30_000, labels[1].id, methods[0].id, null, at('09:00'))
    await updateExpense(id, {
      amountCentavos: 30_000, labelId: labels[1].id, paymentMethodId: methods[0].id, timestamp: at('09:00'),
      description: null, notes: 'late, "really"',
    })

    const day = await exportDay('2026-10-16', 'csv')
    expect(day.name).toBe('expenses-2026-10-16.csv')
    expect(day.mime).toBe('text/csv')
    const lines = text(day.bytes).split('\n')
    expect(lines[1]).toContain('09:00,Transpo')
    expect(lines[2]).toContain('12:30,Food,GCash,Jollibee')

    const month = await exportMonth('2026-10', 'xlsx')
    expect(month.name).toBe('expenses-2026-10.xlsx')
    const files = unzipSync(month.bytes)
    const workbook = strFromU8(files['xl/workbook.xml'])
    expect(workbook).toContain('name="Expenses"')
    expect(workbook).toContain('name="By label"')
    expect(workbook).toContain('name="By payment"')
    expect(strFromU8(files['xl/worksheets/sheet1.xml'])).toContain('<f>SUM(F2:F3)</f>')
    const byLabel = strFromU8(files['xl/worksheets/sheet2.xml'])
    expect(byLabel.indexOf('Transpo')).toBeLessThan(byLabel.indexOf('Food')) // largest first
  })
})
