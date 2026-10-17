export interface Rect {
  left: number
  top: number
  right: number
  bottom: number
}

/** The spotlight hole: the target grown by `padding` on every side. */
export function cutout(target: Rect, padding: number): Rect {
  return { left: target.left - padding, top: target.top - padding, right: target.right + padding, bottom: target.bottom + padding }
}

/** Smallest rect that contains all of `rects`; null when the list is empty. */
export function union(rects: Rect[]): Rect | null {
  if (rects.length === 0) return null
  return rects.reduce((a, b) => ({
    left: Math.min(a.left, b.left),
    top: Math.min(a.top, b.top),
    right: Math.max(a.right, b.right),
    bottom: Math.max(a.bottom, b.bottom),
  }))
}

/**
 * Top of the bubble: below the cutout when it fits, otherwise above it when that fits, otherwise on
 * whichever side has more room. Always clamped to [margin, screenHeight - bubbleHeight - margin].
 */
export function bubbleTop(hole: Rect, bubbleHeight: number, screenHeight: number, margin: number): number {
  const below = hole.bottom + margin
  const above = hole.top - margin - bubbleHeight
  const fitsBelow = below + bubbleHeight + margin <= screenHeight
  const fitsAbove = above >= margin
  let top: number
  if (fitsBelow) top = below
  else if (fitsAbove) top = above
  else if (screenHeight - hole.bottom >= hole.top) top = below
  else top = above
  const max = screenHeight - bubbleHeight - margin
  return max < margin ? margin : Math.min(Math.max(top, margin), max)
}
