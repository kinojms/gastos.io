/** Pure rules for swipe-left row actions (Edit, Delete). Mirrored by Android's SwipeRowLogic. */

export const ACTION_WIDTH = 72
export const OPEN_OFFSET = -2 * ACTION_WIDTH
/** Movement before a drag counts as a swipe or a scroll, so taps and small wobbles do neither. */
export const LOCK_SLOP = 10
export const RUBBER = 16
/** px per ms; faster than this decides open/closed by direction alone. */
const FLING = 0.5

/** 'h' once sideways movement wins, 'v' once vertical does (ties scroll), null before the slop. */
export function lockAxis(dx: number, dy: number): 'h' | 'v' | null {
  const ax = Math.abs(dx)
  const ay = Math.abs(dy)
  if (Math.max(ax, ay) <= LOCK_SLOP) return null
  return ax > ay ? 'h' : 'v'
}

/** Row offset while dragging: never right of closed, at most a short rubber-band past open. */
export function dragOffset(start: number, dx: number): number {
  return Math.min(0, Math.max(OPEN_OFFSET - RUBBER, start + dx))
}

/** Where the row rests after release. */
export function settle(offset: number, velocityX: number): 0 | typeof OPEN_OFFSET {
  if (velocityX < -FLING) return OPEN_OFFSET
  if (velocityX > FLING) return 0
  return offset < OPEN_OFFSET / 2 ? OPEN_OFFSET : 0
}
