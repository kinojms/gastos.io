import { describe, expect, it } from 'vitest'
import {
  addMonths, combineDayTime, dayOf, dayRange, formatFullDate, formatHeaderDate, formatMonth,
  formatTime, formatTime24, monthOf, monthRange, today,
} from './dates'

// vitest setup pins TZ to Asia/Manila (UTC+8)
describe('dates', () => {
  it('runs in Manila time', () => expect(new Date('2026-10-16T16:00:00Z').getHours()).toBe(0))

  it('day range starts at local midnight', () => {
    const [start, end] = dayRange('2026-10-16')
    expect(start).toBe(Date.parse('2026-10-15T16:00:00Z'))
    expect(end - start).toBe(24 * 3600 * 1000)
  })

  it('23:59 local stays on that day, midnight is the next', () => {
    expect(dayOf(Date.parse('2026-10-16T15:59:00Z'))).toBe('2026-10-16')
    expect(dayOf(Date.parse('2026-10-16T16:00:00Z'))).toBe('2026-10-17')
  })

  it('today uses local time', () => expect(today(Date.parse('2026-10-16T16:30:00Z'))).toBe('2026-10-17'))

  it('month helpers', () => {
    expect(monthOf('2026-10-16')).toBe('2026-10')
    expect(addMonths('2026-01', -1)).toBe('2025-12')
    expect(addMonths('2026-12', 1)).toBe('2027-01')
    const [s, e] = monthRange('2026-10')
    expect(s).toBe(Date.parse('2026-09-30T16:00:00Z'))
    expect(e).toBe(Date.parse('2026-10-31T16:00:00Z'))
  })

  it('formats like Android', () => {
    expect(formatHeaderDate('2026-10-16')).toBe('Fri, Oct 16')
    expect(formatFullDate('2026-10-16')).toBe('Oct 16, 2026')
    expect(formatMonth('2026-10')).toBe('October 2026')
  })

  it('formats times', () => {
    const noon = Date.parse('2026-10-16T04:30:00Z') // 12:30 PM Manila
    expect(formatTime(noon)).toBe('12:30 PM')
    expect(formatTime24(noon)).toBe('12:30')
    expect(formatTime(Date.parse('2026-10-15T16:05:00Z'))).toBe('12:05 AM')
    expect(formatTime(Date.parse('2026-10-16T05:30:00Z'))).toBe('1:30 PM')
  })

  it('combines a day and a time', () => {
    expect(combineDayTime('2026-10-16', '12:30')).toBe(Date.parse('2026-10-16T04:30:00Z'))
  })
})
