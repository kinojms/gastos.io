import { onDataChanged } from './data/events'

/**
 * A value loaded from the database that reloads whenever data changes. Call it while a component initialises.
 * Reactive values read before `load`'s first await (a selected day or month) re-run it too.
 */
export function live<T>(load: () => Promise<T>, initial: T) {
  let value = $state(initial)
  let version = $state(0)

  $effect(() => {
    void version
    let stale = false
    load().then(
      (v) => {
        if (!stale) value = v
      },
      () => undefined,
    )
    return () => {
      stale = true
    }
  })

  $effect(() => onDataChanged(() => version++))

  return {
    get value() {
      return value
    },
  }
}
