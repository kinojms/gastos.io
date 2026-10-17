import { strToU8, zipSync } from 'fflate'
import { formatMoney, plainMoney } from '../domain/money'

/** Minimal hand-written OOXML workbook writer; a port of XlsxWriter.kt. */
export type Cell = { text: string } | { peso: number } | { date: string } | 'blank'

export interface Sheet {
  name: string
  header: string[]
  rows: Cell[][]
  totalLabelCol: number
  sumCols: number[]
  wrapCols?: Set<number>
}

const S_DEFAULT = 0
const S_HEADER = 1
const S_PESO = 2
const S_DATE = 3
const S_TOTAL_TEXT = 4
const S_TOTAL_PESO = 5
const S_WRAP = 6

/** Wrapped (long text) columns are never auto-sized wider than this. */
export const WRAP_MAX_WIDTH = 40

const XML = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
const NS = 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'
const REL_NS = 'http://schemas.openxmlformats.org/officeDocument/2006/relationships'

function sumOf(s: Sheet, c: number): number {
  return s.rows.reduce((sum, row) => {
    const cell = row[c]
    return sum + (typeof cell === 'object' && 'peso' in cell ? cell.peso : 0)
  }, 0)
}

function displayLength(cell: Cell): number {
  if (cell === 'blank') return 0
  if ('text' in cell) return cell.text.length
  if ('peso' in cell) return formatMoney(cell.peso).length
  return 10
}

/** Auto-sized column widths (in characters): the longest of header, cells and total row, plus 2. */
export function autoWidths(s: Sheet): number[] {
  const cols = Math.max(s.header.length, ...s.rows.map((r) => r.length), 0)
  return Array.from({ length: cols }, (_, c) => {
    let longest = s.header[c]?.length ?? 0
    for (const row of s.rows) if (row[c] !== undefined) longest = Math.max(longest, displayLength(row[c]))
    if (c === s.totalLabelCol) longest = Math.max(longest, 'Total'.length)
    if (s.sumCols.includes(c)) longest = Math.max(longest, formatMoney(sumOf(s, c)).length)
    const width = longest + 2
    return s.wrapCols?.has(c) ? Math.min(width, WRAP_MAX_WIDTH) : width
  })
}

function esc(s: string): string {
  let out = ''
  for (const ch of s) {
    const code = ch.codePointAt(0)!
    if (ch === '&') out += '&amp;'
    else if (ch === '<') out += '&lt;'
    else if (ch === '>') out += '&gt;'
    else if (ch === '"') out += '&quot;'
    else if (ch === "'") out += '&apos;'
    else if (code < 0x20 && ch !== '\n' && ch !== '\r' && ch !== '\t') continue
    else out += ch
  }
  return out
}

function colName(c: number): string {
  let n = c + 1
  let name = ''
  while (n > 0) {
    const m = (n - 1) % 26
    name = String.fromCharCode(65 + m) + name
    n = Math.floor((n - 1) / 26)
  }
  return name
}

const ref = (c: number, r: number) => `${colName(c)}${r}`

const textCell = (r: string, text: string, style: number) =>
  `<c r="${r}" s="${style}" t="inlineStr"><is><t xml:space="preserve">${esc(text)}</t></is></c>`

/** Excel serial date for a local "YYYY-MM-DD". */
function serial(day: string): number {
  const [y, m, d] = day.split('-').map(Number)
  return Date.UTC(y, m - 1, d) / 86_400_000 + 25569
}

function contentTypes(n: number): string {
  let s = XML + '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
  s += '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
  s += '<Default Extension="xml" ContentType="application/xml"/>'
  s += '<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>'
  s += '<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>'
  for (let i = 1; i <= n; i++) {
    s += `<Override PartName="/xl/worksheets/sheet${i}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>`
  }
  return s + '</Types>'
}

const rootRels = () =>
  XML +
  '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">' +
  `<Relationship Id="rId1" Type="${REL_NS}/officeDocument" Target="xl/workbook.xml"/></Relationships>`

function workbook(sheets: Sheet[]): string {
  let s = XML + `<workbook xmlns="${NS}" xmlns:r="${REL_NS}"><sheets>`
  sheets.forEach((sh, i) => (s += `<sheet name="${esc(sh.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>`))
  return s + '</sheets></workbook>'
}

function workbookRels(n: number): string {
  let s = XML + '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
  for (let i = 1; i <= n; i++) {
    s += `<Relationship Id="rId${i}" Type="${REL_NS}/worksheet" Target="worksheets/sheet${i}.xml"/>`
  }
  s += `<Relationship Id="rId${n + 1}" Type="${REL_NS}/styles" Target="styles.xml"/>`
  return s + '</Relationships>'
}

const styles = () =>
  XML +
  `<styleSheet xmlns="${NS}">` +
  '<numFmts count="2"><numFmt numFmtId="164" formatCode="&quot;₱&quot;#,##0.00"/><numFmt numFmtId="165" formatCode="yyyy-mm-dd"/></numFmts>' +
  '<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>' +
  '<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>' +
  '<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>' +
  '<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>' +
  '<cellXfs count="7">' +
  '<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>' +
  '<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>' +
  '<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>' +
  '<xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>' +
  '<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>' +
  '<xf numFmtId="164" fontId="1" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1"/>' +
  '<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment wrapText="1" vertical="top"/></xf>' +
  '</cellXfs>' +
  '<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>' +
  '</styleSheet>'

function sheetXml(s: Sheet): string {
  let x = XML + `<worksheet xmlns="${NS}"><sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" state="frozen"/></sheetView></sheetViews>`
  const widths = autoWidths(s)
  if (widths.length) {
    x += '<cols>'
    widths.forEach((w, i) => (x += `<col min="${i + 1}" max="${i + 1}" width="${w}" customWidth="1"/>`))
    x += '</cols>'
  }
  x += '<sheetData><row r="1">'
  s.header.forEach((h, c) => (x += textCell(ref(c, 1), h, S_HEADER)))
  x += '</row>'
  s.rows.forEach((row, i) => {
    const r = i + 2
    x += `<row r="${r}">`
    row.forEach((cell, c) => {
      if (cell === 'blank') return
      if ('text' in cell) x += textCell(ref(c, r), cell.text, s.wrapCols?.has(c) ? S_WRAP : S_DEFAULT)
      else if ('peso' in cell) x += `<c r="${ref(c, r)}" s="${S_PESO}"><v>${plainMoney(cell.peso)}</v></c>`
      else x += `<c r="${ref(c, r)}" s="${S_DATE}"><v>${serial(cell.date)}</v></c>`
    })
    x += '</row>'
  })
  const tr = s.rows.length + 2
  x += `<row r="${tr}">`
  const cols = [...new Set([...s.sumCols, s.totalLabelCol])].sort((a, b) => a - b)
  for (const c of cols) {
    if (c === s.totalLabelCol) {
      x += textCell(ref(c, tr), 'Total', S_TOTAL_TEXT)
    } else {
      const v = plainMoney(sumOf(s, c))
      const col = colName(c)
      x += s.rows.length === 0
        ? `<c r="${ref(c, tr)}" s="${S_TOTAL_PESO}"><v>${v}</v></c>`
        : `<c r="${ref(c, tr)}" s="${S_TOTAL_PESO}"><f>SUM(${col}2:${col}${tr - 1})</f><v>${v}</v></c>`
    }
  }
  return x + '</row></sheetData></worksheet>'
}

export function writeXlsx(sheets: Sheet[]): Uint8Array {
  const files: Record<string, Uint8Array> = {
    '[Content_Types].xml': strToU8(contentTypes(sheets.length)),
    '_rels/.rels': strToU8(rootRels()),
    'xl/workbook.xml': strToU8(workbook(sheets)),
    'xl/_rels/workbook.xml.rels': strToU8(workbookRels(sheets.length)),
    'xl/styles.xml': strToU8(styles()),
  }
  sheets.forEach((s, i) => (files[`xl/worksheets/sheet${i + 1}.xml`] = strToU8(sheetXml(s))))
  return zipSync(files)
}
