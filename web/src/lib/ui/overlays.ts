/**
 * Back-button support for dialogs and sheets. Opening one pushes a history entry, so the phone's back
 * gesture (the iOS edge swipe) closes the top overlay instead of leaving the screen.
 */
export interface OverlayEntry {
  close: () => void
}

const stack: OverlayEntry[] = []
let silentPops = 0

/** Registers an open overlay. `close` runs when the user navigates back. */
export function openOverlay(close: () => void): OverlayEntry {
  const entry: OverlayEntry = { close }
  history.pushState({ overlay: true }, '')
  stack.push(entry)
  return entry
}

/** Removes an overlay that was closed some other way (button, scrim) and drops its history entry. */
export function dismissOverlay(entry: OverlayEntry): void {
  const i = stack.indexOf(entry)
  if (i < 0) return
  stack.splice(i, 1)
  silentPops++
  history.back()
}

/** True while at least one overlay is open. */
export function hasOverlay(): boolean {
  return stack.length > 0
}

window.addEventListener('popstate', () => {
  if (silentPops > 0) {
    silentPops--
    return
  }
  stack.pop()?.close()
})
