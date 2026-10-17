import { beforeEach, describe, expect, it } from 'vitest'
import v2 from './fixtures/android-v2.json?raw'
import { openGastosDb } from '../data/db'
import { addExpense, allExpenses } from '../data/expenses'
import { allLabels } from '../data/labels'
import { allMethods } from '../data/methods'
import { getPref, setPref } from '../data/prefs'
import { GitHubError } from './github'
import {
  backupNow, restoreFromFile, restoreFromGitHub, saveSettings, setGithubApi, startFresh, type GithubApi,
} from './manager'

class FakeGithub implements GithubApi {
  calls: string[] = []
  uploads: { content: string; sha: string | null; message: string }[] = []
  remote: string | null = null
  sha: string | null = null
  failWith: Error | null = null

  async getSha(repo: string, path: string, token: string) {
    this.calls.push(`getSha ${repo} ${path} ${token}`)
    if (this.failWith) throw this.failWith
    return this.sha
  }
  async download(repo: string, path: string, token: string) {
    this.calls.push(`download ${repo} ${path} ${token}`)
    if (this.failWith) throw this.failWith
    return this.remote
  }
  async upload(repo: string, path: string, token: string, content: string, sha: string | null, message: string) {
    this.calls.push(`upload ${repo} ${path} ${token}`)
    if (this.failWith) throw this.failWith
    this.uploads.push({ content, sha, message })
  }
}

let gh: FakeGithub
let n = 0
beforeEach(async () => {
  await openGastosDb('m' + n++)
  gh = new FakeGithub()
  setGithubApi(gh)
})

async function seed() {
  const labels = await allLabels()
  const methods = await allMethods()
  await addExpense(500, labels[0].id, methods[0].id, null, Date.parse('2026-10-16T04:00:00Z'))
}

describe('backupNow', () => {
  it('does nothing while disabled', async () => {
    await setPref('backup_repo', 'me/data')
    await setPref('backup_token', 'tok')
    expect(await backupNow()).toEqual({ ok: true })
    expect(gh.calls).toEqual([])
  })

  it('is a silent success when enabled without a repo or token', async () => {
    await setPref('backup_enabled', true)
    expect(await backupNow()).toEqual({ ok: true })
    expect(gh.calls).toEqual([])
    expect(await getPref('last_backup_error')).toBeUndefined()
    await setPref('backup_repo', 'me/data')
    expect(await backupNow()).toEqual({ ok: true })
    expect(gh.calls).toEqual([])
  })

  it('uploads schema 2 JSON with the commit message and records the time', async () => {
    await seed()
    await setPref('backup_repo', 'me/data')
    await setPref('backup_token', 'tok')
    await setPref('backup_enabled', true)
    gh.sha = 'abc'
    expect(await backupNow()).toEqual({ ok: true })
    const up = gh.uploads[0]
    expect(up.sha).toBe('abc')
    expect(up.message).toMatch(/^Backup \d{4}-\d{2}-\d{2} \d{2}:\d{2}$/)
    expect(JSON.parse(up.content).schemaVersion).toBe(2)
    expect(JSON.parse(up.content).expenses).toHaveLength(1)
    expect(typeof (await getPref('last_backup_at'))).toBe('number')
    expect(await getPref('last_backup_error')).toBeUndefined()
  })

  it('records "No internet connection." and returns it, without throwing', async () => {
    await setPref('backup_repo', 'me/data')
    await setPref('backup_token', 'tok')
    await setPref('backup_enabled', true)
    gh.failWith = new GitHubError(0, 'No internet connection.')
    expect(await backupNow()).toEqual({ ok: false, message: 'No internet connection.' })
    expect(await getPref('last_backup_error')).toBe('No internet connection.')
  })
})

describe('restore and start fresh', () => {
  it('restoreFromFile replaces data and turns backup on', async () => {
    await seed()
    expect(await restoreFromFile(v2)).toEqual({ ok: true })
    expect((await allExpenses()).map((e) => e.id)).toEqual([40, 41, 42])
    expect(await getPref('backup_enabled')).toBe(true)
    expect(gh.calls).toEqual([])
  })

  it('a bad file changes nothing and returns the message', async () => {
    await seed()
    const r = await restoreFromFile('nope')
    expect(r).toEqual({ ok: false, message: "This backup file is damaged or isn't a Gastos.io backup." })
    expect(await allExpenses()).toHaveLength(1)
    expect(await getPref('backup_enabled')).toBeUndefined()
  })

  it('restoreFromGitHub needs a repo and token, and reports a missing backup', async () => {
    expect(await restoreFromGitHub()).toEqual({ ok: false, message: 'Set the GitHub repo first.' })
    await setPref('backup_repo', 'me/data')
    expect(await restoreFromGitHub()).toEqual({ ok: false, message: 'Add your GitHub token first.' })
    await setPref('backup_token', 'tok')
    expect(await restoreFromGitHub()).toEqual({ ok: false, message: 'No backup found in this repo.' })
    gh.remote = v2
    expect(await restoreFromGitHub()).toEqual({ ok: true })
    expect(await getPref('backup_enabled')).toBe(true)
    expect(await allExpenses()).toHaveLength(3)
  })

  it('startFresh turns backup on and uploads', async () => {
    await seed()
    await setPref('backup_repo', 'me/data')
    await setPref('backup_token', 'tok')
    expect(await startFresh()).toEqual({ ok: true })
    expect(await getPref('backup_enabled')).toBe(true)
    expect(gh.uploads).toHaveLength(1)
  })
})

describe('saveSettings', () => {
  it('pauses backup when the repo changes or a token is typed', async () => {
    await setPref('backup_repo', 'me/old')
    await setPref('backup_token', 'old')
    await setPref('backup_enabled', true)
    await saveSettings('me/new', '')
    expect(await getPref('backup_repo')).toBe('me/new')
    expect(await getPref('backup_enabled')).toBe(false)

    await setPref('backup_enabled', true)
    await saveSettings('me/new', 'fresh-token')
    expect(await getPref('backup_token')).toBe('fresh-token')
    expect(await getPref('backup_enabled')).toBe(false)
  })

  it('keeps backup on when nothing changed and the token field is empty', async () => {
    await setPref('backup_repo', 'me/data')
    await setPref('backup_token', 'tok')
    await setPref('backup_enabled', true)
    await saveSettings(' me/data ', '')
    expect(await getPref('backup_enabled')).toBe(true)
    expect(await getPref('backup_token')).toBe('tok')
  })
})
