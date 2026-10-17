import { describe, expect, it } from 'vitest'
import { cleanDescription, cleanNotes } from './expenseText'

describe('expenseText', () => {
  it('trims and nulls blank descriptions', () => {
    expect(cleanDescription('   ')).toBeNull()
    expect(cleanDescription(null)).toBeNull()
    expect(cleanDescription(undefined)).toBeNull()
    expect(cleanDescription('  Jollibee  ')).toBe('Jollibee')
  })

  it('cuts descriptions at 60 then trims the end', () => {
    expect(cleanDescription('x'.repeat(80))).toHaveLength(60)
    expect(cleanDescription('a'.repeat(59) + ' bbb')).toBe('a'.repeat(59))
  })

  it('trims and nulls blank notes, cuts at 1000', () => {
    expect(cleanNotes('\n  \t')).toBeNull()
    expect(cleanNotes(' paid half\n')).toBe('paid half')
    expect(cleanNotes('n'.repeat(1200))).toHaveLength(1000)
  })
})
