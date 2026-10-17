const pad2 = (n: number) => String(n).padStart(2, '0')

const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
const MONTHS_SHORT = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
const MONTHS_LONG = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
]

function localDate(day: string): Date {
  const [y, m, d] = day.split('-').map(Number)
  return new Date(y, m - 1, d)
}

/** "YYYY-MM-DD" of a local Date. */
export function toDay(d: Date): string {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
}

/** Today as "YYYY-MM-DD" in the device's local time. */
export function today(now: number = Date.now()): string {
  return toDay(new Date(now))
}

/** The local day a timestamp falls on. */
export function dayOf(ts: number): string {
  return toDay(new Date(ts))
}

/** [start, end) epoch millis of a local day. */
export function dayRange(day: string): [number, number] {
  const start = localDate(day)
  const end = new Date(start.getFullYear(), start.getMonth(), start.getDate() + 1)
  return [start.getTime(), end.getTime()]
}

/** "YYYY-MM" of a day or month string. */
export function monthOf(day: string): string {
  return day.slice(0, 7)
}

/** [start, end) epoch millis of a local month ("YYYY-MM"). */
export function monthRange(month: string): [number, number] {
  const [y, m] = month.split('-').map(Number)
  return [new Date(y, m - 1, 1).getTime(), new Date(y, m, 1).getTime()]
}

export function addMonths(month: string, n: number): string {
  const [y, m] = month.split('-').map(Number)
  const d = new Date(y, m - 1 + n, 1)
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}`
}

/** "Fri, Oct 16": Home header and day rows. */
export function formatHeaderDate(day: string): string {
  const d = localDate(day)
  return `${WEEKDAYS[d.getDay()]}, ${MONTHS_SHORT[d.getMonth()]} ${d.getDate()}`
}

/** Same format the Android Days tab uses for a day row. */
export const formatDayRow = formatHeaderDate

/** "Oct 16, 2026": the Edit expense date line. */
export function formatFullDate(day: string): string {
  const d = localDate(day)
  return `${MONTHS_SHORT[d.getMonth()]} ${d.getDate()}, ${d.getFullYear()}`
}

/** "October 2026": the Month switcher. */
export function formatMonth(month: string): string {
  const [y, m] = month.split('-').map(Number)
  return `${MONTHS_LONG[m - 1]} ${y}`
}

/** "12:30 PM". */
export function formatTime(ts: number): string {
  const d = new Date(ts)
  const h = d.getHours()
  return `${h % 12 === 0 ? 12 : h % 12}:${pad2(d.getMinutes())} ${h < 12 ? 'AM' : 'PM'}`
}

/** "12:30" (24-hour, for CSV). */
export function formatTime24(ts: number): string {
  const d = new Date(ts)
  return `${pad2(d.getHours())}:${pad2(d.getMinutes())}`
}

/** "2026-10-16" and "13:05" to epoch millis in local time (Edit expense). */
export function combineDayTime(day: string, time: string): number {
  const [y, m, d] = day.split('-').map(Number)
  const [hh, mm] = time.split(':').map(Number)
  return new Date(y, m - 1, d, hh, mm).getTime()
}
