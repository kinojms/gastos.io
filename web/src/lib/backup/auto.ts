import { onDataChanged } from '../data/events'
import { getPref, setPref } from '../data/prefs'
import { backupNow } from './manager'

const DELAY_MS = 30_000

/**
 * iOS gives web apps no background time, so back up while the app is open: 30 seconds after the last change,
 * when the page is hidden, and at launch if anything changed since the last backup. Never throws.
 */
export function startAutoBackup(delayMs: number = DELAY_MS): () => void {
  let timer: ReturnType<typeof setTimeout> | null = null

  const run = async () => {
    if (timer !== null) clearTimeout(timer)
    timer = null
    if (typeof navigator !== 'undefined' && navigator.onLine === false) return
    try {
      await backupNow()
    } catch {
      // backupNow records its own errors; nothing may escape from here
    }
  }

  const schedule = () => {
    if (timer !== null) clearTimeout(timer)
    timer = setTimeout(() => void run(), delayMs)
  }

  const offChanged = onDataChanged(() => {
    void setPref('last_change_at', Date.now()).catch(() => undefined)
    schedule()
  })

  const onVisibility = () => {
    if (document.visibilityState === 'hidden' && timer !== null) void run()
  }
  document.addEventListener('visibilitychange', onVisibility)

  void (async () => {
    try {
      const changed = (await getPref('last_change_at')) ?? 0
      const backedUp = (await getPref('last_backup_at')) ?? 0
      if (changed > backedUp) await run()
    } catch {
      // ignore: auto-backup is best effort
    }
  })()

  return () => {
    offChanged()
    document.removeEventListener('visibilitychange', onVisibility)
    if (timer !== null) clearTimeout(timer)
    timer = null
  }
}
