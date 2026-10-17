import { describe, expect, it } from 'vitest'
import { DAY_MS, credentialsChanged, shouldShowReminder } from './rules'

describe('credentialsChanged', () => {
  it('a first repo counts as changed', () => expect(credentialsChanged(undefined, 'me/data', false)).toBe(true))
  it('same repo with no token typed is unchanged', () => expect(credentialsChanged('me/data', 'me/data', false)).toBe(false))
  it('ignores case and spaces', () => expect(credentialsChanged('me/data', ' Me/Data ', false)).toBe(false))
  it('a different repo is changed', () => expect(credentialsChanged('me/data', 'me/other', false)).toBe(true))
  it('a typed token is changed', () => expect(credentialsChanged('me/data', 'me/data', true)).toBe(true))
})

describe('shouldShowReminder', () => {
  const now = 100 * DAY_MS
  const show = (o: Partial<Parameters<typeof shouldShowReminder>[0]> = {}) =>
    shouldShowReminder({
      now, hasGitHub: false, lastFileBackupAt: null, oldestExpenseAt: now - 30 * DAY_MS, dismissedAt: null, ...o,
    })

  it('is hidden with no expenses', () => expect(show({ oldestExpenseAt: null })).toBe(false))
  it('is hidden when GitHub is set up', () => expect(show({ hasGitHub: true })).toBe(false))
  it('is hidden when the oldest expense is 13 days old', () => expect(show({ oldestExpenseAt: now - 13 * DAY_MS })).toBe(false))
  it('shows when the oldest expense is 14 days old', () => expect(show({ oldestExpenseAt: now - 14 * DAY_MS })).toBe(true))
  it('is hidden after a recent file backup', () => expect(show({ lastFileBackupAt: now - 3 * DAY_MS })).toBe(false))
  it('shows when the file backup is 15 days old', () => expect(show({ lastFileBackupAt: now - 15 * DAY_MS })).toBe(true))
  it('is hidden when dismissed hours ago', () => expect(show({ dismissedAt: now - DAY_MS / 12 })).toBe(false))
  it('shows when dismissed 25 hours ago', () => expect(show({ dismissedAt: now - (25 * DAY_MS) / 24 })).toBe(true))
})
