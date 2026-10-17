import { describe, expect, it } from 'vitest'
import { GitHubError, download, getSha, upload } from './github'

type Call = { url: string; init?: RequestInit }

function fakeFetch(responses: (Response | Error)[], calls: Call[] = []) {
  const fn = (async (url: string, init?: RequestInit) => {
    calls.push({ url: String(url), init })
    const next = responses.shift()
    if (!next) throw new Error('unexpected fetch')
    if (next instanceof Error) throw next
    return next
  }) as unknown as typeof fetch
  return { fn, calls }
}

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status })

describe('github', () => {
  it('maps 401 to the Android message', async () => {
    const { fn } = fakeFetch([new Response('', { status: 401 })])
    await expect(getSha('me/data', 'backup.json', 'tok', fn)).rejects.toMatchObject({
      message: 'GitHub token is invalid or expired.', code: 401, retryable: false,
    })
  })

  it('maps a thrown network error to code 0', async () => {
    const { fn } = fakeFetch([new TypeError('Failed to fetch')])
    const err = await getSha('me/data', 'backup.json', 'tok', fn).catch((e) => e)
    expect(err).toBeInstanceOf(GitHubError)
    expect(err).toMatchObject({ message: 'No internet connection.', code: 0, retryable: true })
  })

  it('treats 5xx as retryable and others by status', async () => {
    const e500 = await getSha('me/data', 'b', 't', fakeFetch([new Response('', { status: 503 })]).fn).catch((e) => e)
    expect(e500).toMatchObject({ message: 'GitHub error (503). Try again later.', retryable: true })
    const e403 = await getSha('me/data', 'b', 't', fakeFetch([new Response('', { status: 403 })]).fn).catch((e) => e)
    expect(e403.message).toBe("Token can't access this repo.")
  })

  it('getSha returns the sha, or null on 404', async () => {
    expect(await getSha('me/data', 'backup.json', 'tok', fakeFetch([json({ sha: 'abc' })]).fn)).toBe('abc')
    expect(await getSha('me/data', 'backup.json', 'tok', fakeFetch([new Response('', { status: 404 })]).fn)).toBeNull()
  })

  it('sends the auth and API version headers to the contents URL', async () => {
    const { fn, calls } = fakeFetch([json({ sha: 'abc' })])
    await getSha('me/data', 'backup.json', 'tok', fn)
    expect(calls[0].url).toBe('https://api.github.com/repos/me/data/contents/backup.json')
    const h = new Headers(calls[0].init?.headers)
    expect(h.get('Authorization')).toBe('Bearer tok')
    expect(h.get('X-GitHub-Api-Version')).toBe('2022-11-28')
  })

  it('upload sends base64 of the UTF-8 bytes', async () => {
    const { fn, calls } = fakeFetch([new Response('{}', { status: 201 })])
    await upload('me/data', 'backup.json', 'tok', '₱ ñ', 'oldsha', 'Backup 2026-10-16 12:00', fn)
    expect(calls[0].init?.method).toBe('PUT')
    const body = JSON.parse(String(calls[0].init?.body))
    expect(body.message).toBe('Backup 2026-10-16 12:00')
    expect(body.sha).toBe('oldsha')
    expect(Buffer.from(body.content, 'base64').toString('utf8')).toBe('₱ ñ')
  })

  it('upload omits sha when the file is new', async () => {
    const { fn, calls } = fakeFetch([new Response('{}', { status: 201 })])
    await upload('me/data', 'backup.json', 'tok', 'x', null, 'm', fn)
    expect('sha' in JSON.parse(String(calls[0].init?.body))).toBe(false)
  })

  it('download returns the raw text', async () => {
    const { fn, calls } = fakeFetch([new Response('{"a":1}', { status: 200 })])
    expect(await download('me/data', 'backup.json', 'tok', fn)).toBe('{"a":1}')
    expect(new Headers(calls[0].init?.headers).get('Accept')).toBe('application/vnd.github.raw+json')
  })

  it('download returns null when only the file is missing', async () => {
    const { fn } = fakeFetch([new Response('', { status: 404 }), json({ id: 1 })])
    expect(await download('me/data', 'backup.json', 'tok', fn)).toBeNull()
  })

  it('download throws when the repo itself is hidden', async () => {
    const { fn } = fakeFetch([new Response('', { status: 404 }), new Response('', { status: 404 })])
    await expect(download('me/data', 'backup.json', 'tok', fn)).rejects.toThrow("Repo not found, or this token can't access it.")
  })

  it('rejects a malformed repo', async () => {
    const { fn } = fakeFetch([])
    await expect(getSha('nope', 'backup.json', 'tok', fn)).rejects.toThrow('Repo must look like owner/name.')
    await expect(getSha('a/b/c', 'backup.json', 'tok', fn)).rejects.toThrow('Repo must look like owner/name.')
  })
})
