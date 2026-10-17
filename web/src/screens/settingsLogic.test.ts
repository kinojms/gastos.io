import { describe, expect, it } from 'vitest'
import {
  NOT_BACKED_UP, NO_FILE_BACKUP, REPO_ERROR, backupFileName, formatBackupStatus, formatFileStatus, formatStamp,
  validateRepo,
} from './settingsLogic'

const NOON = Date.parse('2026-10-16T04:30:00Z') // 12:30 PM Manila

describe('settings logic', () => {
  it('validates owner/name', () => {
    for (const bad of ['', 'owner', 'owner/', '/repo', 'a/b/c', 'a b/c']) expect(validateRepo(bad), bad).toBe(REPO_ERROR)
    expect(validateRepo('My-Name/repo_1.x')).toBeNull()
    expect(validateRepo(' me/data ')).toBeNull()
  })

  it('formats stamps like Android', () => expect(formatStamp(NOON)).toBe('Oct 16, 12:30 PM'))

  it('a failed backup wins over the time', () => {
    expect(formatBackupStatus(NOON, 'No internet connection.')).toEqual({ text: 'No internet connection.', isError: true })
    expect(formatBackupStatus(NOON, undefined)).toEqual({ text: 'Last backup: Oct 16, 12:30 PM', isError: false })
    expect(formatBackupStatus(undefined, undefined)).toEqual({ text: NOT_BACKED_UP, isError: false })
  })

  it('formats the file backup line', () => {
    expect(formatFileStatus(undefined)).toBe(NO_FILE_BACKUP)
    expect(formatFileStatus(NOON)).toBe('Last file backup: Oct 16, 12:30 PM')
  })

  it('names the backup file by date', () => expect(backupFileName('2026-10-16')).toBe('gastos-backup-2026-10-16.json'))
})
