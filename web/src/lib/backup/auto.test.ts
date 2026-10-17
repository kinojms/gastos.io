import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { openGastosDb } from '../data/db'
import { addExpense } from '../data/expenses'
import { allLabels } from '../data/labels'
import { allMethods } from '../data/methods'
import { getPref, setPref } from '../data/prefs'
import { startAutoBackup } from './auto'
import { GitHubError } from './github'
import { setGithubApi, type GithubApi } from './manager'

let uploads = 0
let failureCalls = 0
let failing: Error | null = null
const fake: GithubApi = {
  async getSha() {
    if (failing) {
      failureCalls++
      throw failing
    }
    return null
  },
  async download() {
    return null
  },
  async upload() {
    if (failing) throw failing
    uploads++
  },
}

/** fake-indexeddb finishes work on real macrotasks, which fake timers don't advance; let them drain. */
const settle = async () => {
  for (let i = 0; i < 50; i++) await new Promise((r) => setImmediate(r))
}

let n = 0
let stop = () => {}
beforeEach(async () => {
  vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] })
  await openGastosDb('a' + n++)
  uploads = 0
  failureCalls = 0
  failing = null
  setGithubApi(fake)
  await setPref('backup_repo', 'me/data')
  await setPref('backup_token', 'tok')
  await setPref('backup_enabled', true)
})

afterEach(() => {
  stop()
  vi.useRealTimers()
})

async function change() {
  const labels = await allLabels()
  const methods = await allMethods()
  await addExpense(100, labels[0].id, methods[0].id)
}

describe('auto backup', () => {
  it('backs up once, 30 seconds after the last of several changes', async () => {
    stop = startAutoBackup()
    await change()
    await vi.advanceTimersByTimeAsync(10_000)
    await change()
    await vi.advanceTimersByTimeAsync(10_000)
    await change()
    await vi.advanceTimersByTimeAsync(29_000)
    expect(uploads).toBe(0)
    await vi.advanceTimersByTimeAsync(2_000)
    await settle()
    expect(uploads).toBe(1)
  })

  it('backs up right away when the page is hidden with a change pending', async () => {
    stop = startAutoBackup()
    await change()
    Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true })
    document.dispatchEvent(new Event('visibilitychange'))
    await vi.advanceTimersByTimeAsync(10)
    await settle()
    expect(uploads).toBe(1)
    await vi.advanceTimersByTimeAsync(60_000)
    await settle()
    expect(uploads).toBe(1)
    Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true })
  })

  it('backs up at launch when changes are newer than the last backup', async () => {
    await setPref('last_backup_at', 1000)
    await setPref('last_change_at', 2000)
    stop = startAutoBackup()
    await vi.advanceTimersByTimeAsync(10)
    await settle()
    expect(uploads).toBe(1)
  })

  it('stays quiet at launch when nothing changed since the last backup', async () => {
    await setPref('last_backup_at', 5000)
    await setPref('last_change_at', 2000)
    stop = startAutoBackup()
    await vi.advanceTimersByTimeAsync(60_000)
    await settle()
    expect(uploads).toBe(0)
  })

  it('keeps the data and the error when GitHub is unreachable, with no retry storm', async () => {
    failing = new GitHubError(0, 'No internet connection.')
    stop = startAutoBackup()
    await change()
    await vi.advanceTimersByTimeAsync(31_000)
    await settle()
    expect(await getPref('last_backup_error')).toBe('No internet connection.')
    await vi.advanceTimersByTimeAsync(10 * 60_000)
    await settle()
    expect(uploads).toBe(0)
    expect(failureCalls).toBe(1)
  })
})
