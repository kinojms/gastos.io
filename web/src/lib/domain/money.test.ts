import { describe, expect, it } from 'vitest'
import { MAX_CENTAVOS, formatMoney, plainMoney } from './money'

describe('money', () => {
  it('formats pesos', () => {
    expect(formatMoney(4550)).toBe('₱45.50')
    expect(formatMoney(123450)).toBe('₱1,234.50')
    expect(formatMoney(0)).toBe('₱0.00')
    expect(formatMoney(5)).toBe('₱0.05')
    expect(formatMoney(100000)).toBe('₱1,000.00')
    expect(formatMoney(MAX_CENTAVOS)).toBe('₱9,999,999.99')
    expect(formatMoney(-4550)).toBe('-₱45.50')
  })

  it('plain has no symbol or grouping', () => {
    expect(plainMoney(4550)).toBe('45.50')
    expect(plainMoney(123450)).toBe('1234.50')
  })
})
