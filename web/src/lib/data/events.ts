const listeners = new Set<() => void>()

/** Subscribes to "some data changed"; returns an unsubscribe function. */
export function onDataChanged(cb: () => void): () => void {
  listeners.add(cb)
  return () => listeners.delete(cb)
}

/** Call after every write so lists refresh and auto-backup is scheduled. */
export function emitDataChanged(): void {
  for (const cb of [...listeners]) cb()
}
