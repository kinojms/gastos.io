import { describe, expect, it } from 'vitest'
import { decideSwipe, stepIndex } from './swipe'

describe('decideSwipe', () => {
  it('left is the next method', () => expect(decideSwipe(-60, 10, 48)).toEqual({ axis: 'METHOD', delta: 1 }))
  it('right is the previous method', () => expect(decideSwipe(60, 10, 48)).toEqual({ axis: 'METHOD', delta: -1 }))
  it('up is the next label', () => expect(decideSwipe(5, -70, 48)).toEqual({ axis: 'LABEL', delta: 1 }))
  it('down is the previous label', () => expect(decideSwipe(5, 70, 48)).toEqual({ axis: 'LABEL', delta: -1 }))
  it('short drags are taps', () => expect(decideSwipe(30, 30, 48)).toEqual({ axis: 'NONE', delta: 0 }))
  it('uses 48px by default', () => expect(decideSwipe(47, 0).axis).toBe('NONE'))
})

describe('stepIndex', () => {
  it('wraps forward', () => expect(stepIndex(3, 2, 1)).toBe(0))
  it('wraps backward', () => expect(stepIndex(3, 0, -1)).toBe(2))
  it('is 0 for an empty list', () => expect(stepIndex(0, 0, 1)).toBe(0))
})
