import { describe, expect, it } from 'vitest'
import { applyKey, displayAmount, toCentavos } from './amountInput'
import { MAX_CENTAVOS } from './money'

const d = (n: number) => ({ digit: n })

describe('applyKey', () => {
  it('appends digits', () => expect(applyKey('4', d(5))).toBe('45'))
  it('replaces a leading zero', () => expect(applyKey('0', d(7))).toBe('7'))
  it('keeps a single zero on zero', () => expect(applyKey('0', d(0))).toBe('0'))
  it('dot first becomes 0.', () => expect(applyKey('', 'dot')).toBe('0.'))
  it('ignores a second dot', () => expect(applyKey('4.5', 'dot')).toBe('4.5'))
  it('ignores a third decimal', () => expect(applyKey('4.55', d(1))).toBe('4.55'))
  it('appends after 0.', () => expect(applyKey('0.', d(5))).toBe('0.5'))
  it('ignores digits over the max', () => expect(applyKey('9999999', d(1))).toBe('9999999'))
  it('allows exactly the max', () => expect(applyKey('9999999.9', d(9))).toBe('9999999.99'))
  it('ignores a digit past the max', () => expect(applyKey('9999999.99', d(1))).toBe('9999999.99'))
  it('allows a dot at the max integer', () => expect(applyKey('9999999', 'dot')).toBe('9999999.'))
  it('backspaces', () => {
    expect(applyKey('4.5', 'backspace')).toBe('4.')
    expect(applyKey('', 'backspace')).toBe('')
  })
})

describe('toCentavos', () => {
  it('converts', () => {
    expect(toCentavos('45.5')).toBe(4550)
    expect(toCentavos('45.')).toBe(4500)
    expect(toCentavos('12.5')).toBe(1250)
    expect(toCentavos('9999999.99')).toBe(MAX_CENTAVOS)
  })
  it('is null for empty, zero and dot', () => {
    expect(toCentavos('')).toBeNull()
    expect(toCentavos('0')).toBeNull()
    expect(toCentavos('0.00')).toBeNull()
    expect(toCentavos('.')).toBeNull()
  })
})

describe('displayAmount', () => {
  it('shows 0 when empty', () => expect(displayAmount('')).toBe('0'))
  it('groups thousands', () => expect(displayAmount('1234.5')).toBe('1,234.5'))
  it('keeps a trailing dot', () => expect(displayAmount('45.')).toBe('45.'))
})
