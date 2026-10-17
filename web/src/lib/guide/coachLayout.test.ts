import { describe, expect, it } from 'vitest'
import { bubbleTop, cutout, union } from './coachLayout'

const rect = (left: number, top: number, right: number, bottom: number) => ({ left, top, right, bottom })

describe('coachLayout', () => {
  it('grows a target by the padding', () => {
    expect(cutout(rect(10, 20, 30, 40), 8)).toEqual(rect(2, 12, 38, 48))
  })

  it('unions rects (numpad keys plus Enter)', () => {
    expect(union([rect(14, 500, 380, 700), rect(14, 710, 380, 770)])).toEqual(rect(14, 500, 380, 770))
    expect(union([])).toBeNull()
  })

  it('puts the bubble below the hole when it fits', () => {
    expect(bubbleTop(rect(0, 100, 100, 200), 120, 800, 16)).toBe(216)
  })

  it('puts it above when below does not fit but above does', () => {
    expect(bubbleTop(rect(0, 500, 100, 700), 120, 800, 16)).toBe(500 - 16 - 120)
  })

  it('picks the side with more room when neither fits, and stays inside the margins', () => {
    const top = bubbleTop(rect(0, 100, 100, 700), 300, 800, 16)
    expect(top).toBeGreaterThanOrEqual(16)
    expect(top).toBeLessThanOrEqual(800 - 300 - 16)
  })

  it('pins to the margin when the bubble is taller than the screen allows', () => {
    expect(bubbleTop(rect(0, 100, 100, 200), 900, 800, 16)).toBe(16)
  })
})
