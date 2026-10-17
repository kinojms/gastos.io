import { formatTime } from '../lib/domain/dates'

export const NOT_BACKED_UP = 'Not backed up yet'
export const NO_FILE_BACKUP = 'No backup file yet'
export const REPO_ERROR = 'Use the format owner/repo'

const REPO_PATTERN = /^[A-Za-z0-9._-]+\/[A-Za-z0-9._-]+$/

/** Null when the owner/name field is well formed, else the message to show. */
export function validateRepo(repo: string): string | null {
  return REPO_PATTERN.test(repo.trim()) ? null : REPO_ERROR
}

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

/** "Oct 16, 12:30 PM": local time, like Android's "MMM d, h:mm a". */
export function formatStamp(ts: number): string {
  const d = new Date(ts)
  return `${MONTHS[d.getMonth()]} ${d.getDate()}, ${formatTime(ts)}`
}

/** A failed last backup wins over the timestamp; with neither, "Not backed up yet". */
export function formatBackupStatus(lastBackupAt: number | undefined, error: string | undefined) {
  if (error) return { text: error, isError: true }
  if (lastBackupAt !== undefined) return { text: `Last backup: ${formatStamp(lastBackupAt)}`, isError: false }
  return { text: NOT_BACKED_UP, isError: false }
}

export function formatFileStatus(lastFileBackupAt: number | undefined): string {
  return lastFileBackupAt === undefined ? NO_FILE_BACKUP : `Last file backup: ${formatStamp(lastFileBackupAt)}`
}

/** "gastos-backup-2026-10-16.json". */
export const backupFileName = (day: string) => `gastos-backup-${day}.json`

/** Files above this are refused as damaged without reading them. */
export const MAX_BACKUP_FILE_BYTES = 20 * 1024 * 1024

export const AboutDev = {
  name: 'Justin Sacdalan',
  tagline: 'keep on keeping on',
  github: 'https://github.com/kinojms',
  linkedin: 'https://www.linkedin.com/in/justin-sacdalan',
}
