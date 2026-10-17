export const DAY_MS = 86_400_000
const STALE_DAYS = 14

/**
 * True when saving settings points backup somewhere new: a token was typed, or the repo differs from the
 * saved one (ignoring case and spaces). Auto-backup then stays off until the owner restores or starts fresh,
 * so a repo that already holds a backup is never silently overwritten.
 */
export function credentialsChanged(savedRepo: string | undefined, newRepo: string, tokenTyped: boolean): boolean {
  return tokenTyped || newRepo.trim().toLowerCase() !== (savedRepo ?? '').trim().toLowerCase()
}

export interface ReminderInput {
  now: number
  hasGitHub: boolean
  lastFileBackupAt: number | null
  oldestExpenseAt: number | null
  dismissedAt: number | null
}

/**
 * Shown when GitHub backup isn't set up, there is data to lose, the last file backup (or, with none,
 * the oldest expense) is at least 14 days old, and the owner hasn't dismissed it in the last day.
 */
export function shouldShowReminder(i: ReminderInput): boolean {
  if (i.hasGitHub || i.oldestExpenseAt === null) return false
  const since = i.lastFileBackupAt ?? i.oldestExpenseAt
  if (i.now - since < STALE_DAYS * DAY_MS) return false
  if (i.dismissedAt !== null && i.now - i.dismissedAt < DAY_MS) return false
  return true
}
