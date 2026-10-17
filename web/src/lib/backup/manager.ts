import { getPref, removePref, setPref } from '../data/prefs'
import { BackupError, exportJson, restoreJson } from './backupFile'
import * as realGithub from './github'
import { credentialsChanged } from './rules'

export type Result = { ok: true } | { ok: false; message: string }

/** The GitHub calls the manager needs; tests swap in a fake. */
export interface GithubApi {
  getSha(repo: string, path: string, token: string): Promise<string | null>
  download(repo: string, path: string, token: string): Promise<string | null>
  upload(repo: string, path: string, token: string, content: string, sha: string | null, message: string): Promise<void>
}

export const PATH = 'backup.json'

let github: GithubApi = realGithub

export function setGithubApi(api: GithubApi): void {
  github = api
}

/** Serialises backup and restore so auto-backup and the buttons never overlap. */
let chain: Promise<unknown> = Promise.resolve()

function serial<T>(task: () => Promise<T>): Promise<T> {
  const run = chain.then(task, task)
  chain = run.catch(() => undefined)
  return run
}

const pad = (n: number) => String(n).padStart(2, '0')

function commitMessage(d: Date): string {
  return `Backup ${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function messageOf(e: unknown): string {
  if (e instanceof realGithub.GitHubError || e instanceof BackupError) return e.message
  return e instanceof Error && e.message ? e.message : 'Something went wrong.'
}

async function savedCredentials(): Promise<{ repo: string; token: string } | null> {
  const repo = (await getPref('backup_repo'))?.trim()
  const token = await getPref('backup_token')
  return repo && token ? { repo, token } : null
}

async function credentials(): Promise<{ repo: string; token: string }> {
  if (!(await getPref('backup_repo'))?.trim()) throw new BackupError('Set the GitHub repo first.')
  const saved = await savedCredentials()
  if (!saved) throw new BackupError('Add your GitHub token first.')
  return saved
}

async function attempt(block: () => Promise<void>): Promise<Result> {
  try {
    await block()
    return { ok: true }
  } catch (e) {
    return { ok: false, message: messageOf(e) }
  }
}

/** Uploads all data. Does nothing (no network) while backup is disabled, or when no GitHub is set up. */
export function backupNow(): Promise<Result> {
  return serial(async () => {
    if (!(await getPref('backup_enabled'))) return { ok: true } as Result
    const saved = await savedCredentials()
    if (!saved) return { ok: true } as Result
    const result = await attempt(async () => {
      const json = await exportJson()
      const sha = await github.getSha(saved.repo, PATH, saved.token)
      await github.upload(saved.repo, PATH, saved.token, json, sha, commitMessage(new Date()))
    })
    if (result.ok) {
      await setPref('last_backup_at', Date.now())
      await removePref('last_backup_error')
    } else {
      await setPref('last_backup_error', result.message)
    }
    return result
  })
}

async function afterRestore(): Promise<void> {
  await setPref('backup_enabled', true)
  await removePref('last_backup_error')
}

/** Replaces local data with the repo's backup. Validated before anything is touched. */
export function restoreFromGitHub(): Promise<Result> {
  return serial(() =>
    attempt(async () => {
      const { repo, token } = await credentials()
      const json = await github.download(repo, PATH, token)
      if (json === null) throw new BackupError('No backup found in this repo.')
      await restoreJson(json)
      await afterRestore()
    }),
  )
}

/** Same as [restoreFromGitHub], but from backup text the user picked as a file. */
export function restoreFromFile(text: string): Promise<Result> {
  return serial(() =>
    attempt(async () => {
      await restoreJson(text)
      await afterRestore()
    }),
  )
}

/** The user chose to keep this phone's data: turn backup on and upload right away. */
export async function startFresh(): Promise<Result> {
  await setPref('backup_enabled', true)
  return backupNow()
}

/**
 * Saves repo and, if one was typed, the token. New or changed credentials pause auto-backup until the
 * user restores or starts fresh. An empty token keeps the existing one.
 */
export async function saveSettings(repoInput: string, tokenInput: string): Promise<void> {
  const repo = repoInput.trim()
  const token = tokenInput.trim()
  const changed = credentialsChanged(await getPref('backup_repo'), repo, token !== '')
  // Token first, so a failed token write never leaves a repo saved without its token.
  if (token) await setPref('backup_token', token)
  await setPref('backup_repo', repo)
  if (changed) await setPref('backup_enabled', false)
}
