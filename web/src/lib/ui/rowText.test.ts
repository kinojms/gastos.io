import { describe, expect, it } from 'vitest'
import type { ExpenseRow } from '../data/models'
import { rowSubtitle, rowTitle } from './rowText'

const row = (o: Partial<ExpenseRow> = {}): ExpenseRow => ({
  id: 1, amountCentavos: 18500, labelId: 1, timestamp: Date.parse('2026-10-16T04:30:00Z'), paymentMethodId: 2,
  description: null, notes: null, labelName: 'Food', labelColor: 0, paymentMethodName: 'GCash', paymentMethodColor: 0,
  ...o,
})

describe('rowText', () => {
  it('uses the description as the title and moves the label into the subtitle', () => {
    const r = row({ description: 'Jollibee' })
    expect(rowTitle(r)).toBe('Jollibee')
    expect(rowSubtitle(r)).toBe('Food · GCash · 12:30 PM')
  })

  it('falls back to the label', () => {
    expect(rowTitle(row())).toBe('Food')
    expect(rowSubtitle(row())).toBe('GCash · 12:30 PM')
  })
})
