import { describe, expect, it } from 'vitest'
import { OPEN_OFFSET, dragOffset, lockAxis, settle } from './swipeRow'

describe('lockAxis', () => {
  it('waits until the finger moves past the slop', () => expect(lockAxis(4, 3)).toBeNull())
  it('locks horizontal when sideways movement wins', () => expect(lockAxis(12, 3)).toBe('h'))
  it('locks vertical when the list is being scrolled', () => expect(lockAxis(3, 12)).toBe('v'))
  it('gives ties to scrolling', () => expect(lockAxis(12, 12)).toBe('v'))
  it('works leftward too', () => expect(lockAxis(-12, 3)).toBe('h'))
})

describe('dragOffset', () => {
  it('never drags right past closed', () => expect(dragOffset(0, 50)).toBe(0))
  it('rubber-bands at most 16 past open', () => expect(dragOffset(0, -500)).toBe(-160))
  it('closes from open when dragged right', () => expect(dragOffset(-144, 200)).toBe(0))
  it('follows the finger in between', () => expect(dragOffset(0, -60)).toBe(-60))
})

describe('settle', () => {
  it('opens past half the actions', () => expect(settle(-80, 0)).toBe(OPEN_OFFSET))
  it('snaps back short of half', () => expect(settle(-60, 0)).toBe(0))
  it('opens on a fast fling left', () => expect(settle(-20, -0.8)).toBe(OPEN_OFFSET))
  it('closes on a fast fling right', () => expect(settle(-140, 0.8)).toBe(0))
  it('opens 144 wide', () => expect(OPEN_OFFSET).toBe(-144))
})
