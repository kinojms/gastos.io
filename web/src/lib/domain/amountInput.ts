import { MAX_CENTAVOS, groupDigits } from './money'

export type Key = { digit: number } | 'dot' | 'backspace'

function parse(text: string): number | null {
  if (text === '' || text === '.') return null
  const dot = text.indexOf('.')
  const intPart = dot < 0 ? text : text.slice(0, dot)
  const decPart = dot < 0 ? '' : text.slice(dot + 1)
  if (decPart.length > 2) return null
  if (!/^\d*$/.test(intPart) || !/^\d*$/.test(decPart)) return null
  if (intPart.length > 12) return Number.MAX_SAFE_INTEGER
  const whole = intPart === '' ? 0 : Number(intPart)
  return whole * 100 + Number(decPart.padEnd(2, '0'))
}

function exceedsMax(text: string): boolean {
  const value = parse(text)
  return value !== null && value > MAX_CENTAVOS
}

/** Pure string-state numpad logic, a port of AmountInput.apply. */
export function applyKey(current: string, key: Key): string {
  if (key === 'backspace') return current.slice(0, -1)
  if (key === 'dot') {
    if (current.includes('.')) return current
    return current === '' ? '0.' : current + '.'
  }
  const base = current === '0' ? '' : current
  const next = base + key.digit
  const decimals = next.includes('.') ? next.slice(next.indexOf('.') + 1) : ''
  return decimals.length > 2 || exceedsMax(next) ? current : next
}

/** Centavos for the text, or null when empty, malformed or zero. */
export function toCentavos(text: string): number | null {
  const value = parse(text)
  return value === null || value === 0 ? null : value
}

/** Text shown on the numpad: "0" when empty, thousands grouped. */
export function displayAmount(text: string): string {
  if (text === '') return '0'
  const dot = text.indexOf('.')
  const intPart = dot < 0 ? text : text.slice(0, dot)
  const rest = dot < 0 ? '' : text.slice(dot)
  return groupDigits(intPart === '' ? '0' : intPart) + rest
}
