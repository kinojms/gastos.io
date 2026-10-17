export const MAX_CENTAVOS = 999_999_999

/** Inserts thousands commas into a string of digits. */
export function groupDigits(digits: string): string {
  return digits.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

function parts(centavos: number): { sign: string; whole: string; frac: string } {
  const abs = Math.abs(centavos)
  return {
    sign: centavos < 0 ? '-' : '',
    whole: String(Math.floor(abs / 100)),
    frac: String(abs % 100).padStart(2, '0'),
  }
}

/** Locale-independent: "₱1,234.50". */
export function formatMoney(centavos: number): string {
  const { sign, whole, frac } = parts(centavos)
  return `${sign}₱${groupDigits(whole)}.${frac}`
}

/** No symbol, no grouping: "1234.50" (for CSV/XLSX). */
export function plainMoney(centavos: number): string {
  const { sign, whole, frac } = parts(centavos)
  return `${sign}${whole}.${frac}`
}
