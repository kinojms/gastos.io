/**
 * Back-button support for dialogs and sheets. Opening one pushes a history entry, so the phone's back
 * gesture (the iOS edge swipe) closes the top overlay instead of leaving the screen.
 *
 * History changes are queued: `history.back()` finishes asynchronously, so a push that follows it right
 * away (a sheet closing as a confirm dialog opens) must wait for the pop to land first.
 */
export interface OverlayEntry {
  close: () => void
}

const stack: OverlayEntry[] = []
let queue: Promise<void> = Promise.resolve()
let pendingBack: (() => void) | null = null

function enqueue(task: () => void | Promise<void>): void {
  queue = queue.then(task).catch(() => undefined)
}

/** Registers an open overlay. `close` runs when the user navigates back. */
export function openOverlay(close: () => void): OverlayEntry {
  const entry: OverlayEntry = { close }
  stack.push(entry)
  enqueue(() => history.pushState({ overlay: true }, ''))
  return entry
}

/** Removes an overlay that was closed some other way (button, scrim) and drops its history entry. */
export function dismissOverlay(entry: OverlayEntry): void {
  const i = stack.indexOf(entry)
  if (i < 0) return
  stack.splice(i, 1)
  enqueue(
    () =>
      new Promise<void>((resolve) => {
        const timer = setTimeout(done, 400) // popstate should land well before this
        function done() {
          clearTimeout(timer)
          if (pendingBack === done) pendingBack = null
          resolve()
        }
        pendingBack = done
        history.back()
      }),
  )
}

/** True while at least one overlay is open. */
export function hasOverlay(): boolean {
  return stack.length > 0
}

window.addEventListener('popstate', () => {
  if (pendingBack) {
    const done = pendingBack
    pendingBack = null
    done()
    return
  }
  stack.pop()?.close()
})
