export type Axis = 'NONE' | 'METHOD' | 'LABEL'

export interface SwipeDecision {
  axis: Axis
  delta: number
}

/** Minimum drag on the dominant axis for a swipe to count (CSS px); shared by Home and the tour. */
export const SWIPE_THRESHOLD_PX = 48

/**
 * Decides a finished drag. The dominant axis wins and must reach the threshold, so taps never count.
 * Left (dx < 0) is the next method, up (dy < 0) is the next label.
 */
export function decideSwipe(dx: number, dy: number, threshold: number = SWIPE_THRESHOLD_PX): SwipeDecision {
  const ax = Math.abs(dx)
  const ay = Math.abs(dy)
  if (Math.max(ax, ay) < threshold) return { axis: 'NONE', delta: 0 }
  if (ax > ay) return { axis: 'METHOD', delta: dx < 0 ? 1 : -1 }
  return { axis: 'LABEL', delta: dy < 0 ? 1 : -1 }
}

/** Index delta steps from current, wrapping around len; 0 when len is 0. */
export function stepIndex(len: number, current: number, delta: number): number {
  if (len === 0) return 0
  return (((current + delta) % len) + len) % len
}
