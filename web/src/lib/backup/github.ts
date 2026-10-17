/** A GitHub call failed. `message` is safe to show; it never contains the token. */
export class GitHubError extends Error {
  /** HTTP status, or 0 when no response arrived. */
  code: number

  constructor(code: number, message: string) {
    super(message)
    this.code = code
  }

  /** Network failures and server-side errors are worth retrying; auth and repo errors are not. */
  get retryable(): boolean {
    return this.code === 0 || this.code >= 500
  }
}

const API = 'https://api.github.com/'

function repoParts(repo: string): [string, string] {
  const parts = repo.trim().split('/')
  if (parts.length !== 2 || parts.some((p) => !p.trim())) throw new GitHubError(400, 'Repo must look like owner/name.')
  return [parts[0], parts[1]]
}

/** repos/{owner}/{name}/contents/{path}, or repos/{owner}/{name} when path is null. */
function url(repo: string, path: string | null): string {
  const [owner, name] = repoParts(repo)
  const base = `${API}repos/${encodeURIComponent(owner)}/${encodeURIComponent(name)}`
  return path === null ? base : `${base}/contents/${path.split('/').map(encodeURIComponent).join('/')}`
}

function headers(token: string, accept: string): Record<string, string> {
  return { Authorization: `Bearer ${token}`, 'X-GitHub-Api-Version': '2022-11-28', Accept: accept }
}

function httpError(code: number): GitHubError {
  switch (code) {
    case 401: return new GitHubError(code, 'GitHub token is invalid or expired.')
    case 403: return new GitHubError(code, "Token can't access this repo.")
    case 404: return new GitHubError(code, 'Repo not found.')
    default: return new GitHubError(code, `GitHub error (${code}). Try again later.`)
  }
}

/** 404 yields null when nullOn404; other failures throw [GitHubError]. */
async function execute<T>(
  fetchFn: typeof fetch,
  target: string,
  init: RequestInit,
  nullOn404: boolean,
  onSuccess: (r: Response) => Promise<T>,
): Promise<T | null> {
  let response: Response
  try {
    response = await fetchFn(target, init)
  } catch {
    throw new GitHubError(0, 'No internet connection.')
  }
  if (response.ok) return onSuccess(response)
  if (response.status === 404 && nullOn404) return null
  throw httpError(response.status)
}

const JSON_ACCEPT = 'application/vnd.github+json'
const RAW_ACCEPT = 'application/vnd.github.raw+json'

/** Blob sha of the file, or null if it does not exist. */
export async function getSha(
  repo: string, path: string, token: string, fetchFn: typeof fetch = (...a) => fetch(...a),
): Promise<string | null> {
  return execute(fetchFn, url(repo, path), { method: 'GET', headers: headers(token, JSON_ACCEPT) }, true, async (r) => {
    const body = (await r.json().catch(() => null)) as { sha?: unknown } | null
    if (typeof body?.sha !== 'string') throw new GitHubError(r.status, 'Unexpected response from GitHub.')
    return body.sha
  })
}

/**
 * Raw file contents, or null if the repo is visible but has no such file. GitHub also answers 404 for a
 * private repo the token can't see, so a missing file is double-checked against the repo itself.
 */
export async function download(
  repo: string, path: string, token: string, fetchFn: typeof fetch = (...a) => fetch(...a),
): Promise<string | null> {
  const content = await execute(
    fetchFn, url(repo, path), { method: 'GET', headers: headers(token, RAW_ACCEPT) }, true, (r) => r.text(),
  )
  if (content === null) {
    const visible = await execute(
      fetchFn, url(repo, null), { method: 'GET', headers: headers(token, JSON_ACCEPT) }, true, async () => true,
    )
    if (visible === null) throw new GitHubError(404, "Repo not found, or this token can't access it.")
  }
  return content
}

function toBase64(text: string): string {
  const bytes = new TextEncoder().encode(text)
  let binary = ''
  for (let i = 0; i < bytes.length; i += 0x8000) binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000))
  return btoa(binary)
}

/** Creates or updates the file as one commit. sha is required when the file exists. */
export async function upload(
  repo: string, path: string, token: string, content: string, sha: string | null, message: string,
  fetchFn: typeof fetch = (...a) => fetch(...a),
): Promise<void> {
  const payload: Record<string, string> = { message, content: toBase64(content) }
  if (sha !== null) payload.sha = sha
  await execute(
    fetchFn,
    url(repo, path),
    { method: 'PUT', headers: { ...headers(token, JSON_ACCEPT), 'Content-Type': 'application/json' }, body: JSON.stringify(payload) },
    false,
    async () => undefined,
  )
}
