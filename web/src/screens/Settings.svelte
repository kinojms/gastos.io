<script lang="ts">
  import { onMount } from 'svelte'
  import { exportJson, previewJson } from '../lib/backup/backupFile'
  import {
    backupNow, restoreFromFile, restoreFromGitHub, saveSettings, startFresh, type Result,
  } from '../lib/backup/manager'
  import { prefsStore, setPref } from '../lib/data/prefs'
  import { confirmCopy } from '../lib/domain/confirmCopy'
  import { dayOf, formatFullDate, today } from '../lib/domain/dates'
  import { MIME } from '../lib/export/exportFiles'
  import { navigate } from '../lib/router.svelte'
  import { shareOrDownload } from '../lib/share'
  import { applyTheme } from '../lib/theme/applyTheme'
  import { THEMES, palette, type ThemeId } from '../lib/theme/palettes'
  import ConfirmDialog from '../lib/ui/ConfirmDialog.svelte'
  import NavRow from '../lib/ui/NavRow.svelte'
  import Sheet from '../lib/ui/Sheet.svelte'
  import TopBar from '../lib/ui/TopBar.svelte'
  import { VOICE_LANGUAGES, voiceLanguage, type VoiceLanguageId } from '../lib/voice'
  import {
    AboutDev, MAX_BACKUP_FILE_BYTES, backupFileName, formatBackupStatus, formatFileStatus, validateRepo,
  } from './settingsLogic'

  type Confirm = 'save' | 'restore' | 'fresh' | 'theme' | 'voice' | 'file'

  const DAMAGED = "This backup file is damaged or isn't a Gastos.io backup."

  let repo = $state('')
  let token = $state('')
  let repoError = $state<string | null>(null)
  let busy = $state(false)
  let result = $state<{ text: string; isError: boolean } | null>(null)
  let confirm = $state<Confirm | null>(null)
  let pendingTheme = $state<ThemeId | null>(null)
  let pendingVoice = $state<VoiceLanguageId | null>(null)
  let pendingFile = $state<{ text: string; count: number; date: string } | null>(null)
  let pickingVoice = $state(false)
  let fileInput: HTMLInputElement | undefined = $state()

  const prefs = $derived($prefsStore)
  const hasToken = $derived(Boolean(prefs.backup_token))
  const canUse = $derived(Boolean(prefs.backup_repo?.trim()) && hasToken)
  const enabled = $derived(prefs.backup_enabled === true)
  const status = $derived(formatBackupStatus(prefs.last_backup_at, prefs.last_backup_error))
  const fileStatus = $derived(formatFileStatus(prefs.last_file_backup_at))
  const theme = $derived((prefs.theme_id as ThemeId | undefined) ?? 'CLASSIC')
  const voice = $derived(voiceLanguage(prefs.voice_language))

  onMount(() => {
    repo = prefs.backup_repo ?? ''
  })

  const dark = () => matchMedia('(prefers-color-scheme: dark)').matches

  async function run(success: string, action: () => Promise<Result>) {
    if (busy) return
    busy = true
    result = null
    try {
      const r = await action()
      result = r.ok ? { text: success, isError: false } : { text: r.message, isError: true }
    } finally {
      busy = false
    }
  }

  function askSave() {
    repoError = validateRepo(repo)
    if (!repoError) confirm = 'save'
  }

  async function doSave() {
    confirm = null
    await saveSettings(repo, token)
    token = ''
    result = { text: 'Saved', isError: false }
  }

  async function doTheme() {
    const id = pendingTheme
    confirm = null
    pendingTheme = null
    if (!id || id === theme) return
    await setPref('theme_id', id)
    applyTheme(id)
  }

  async function doVoice() {
    const id = pendingVoice
    confirm = null
    pendingVoice = null
    if (id && id !== voice.id) await setPref('voice_language', id)
  }

  async function saveFile() {
    if (busy) return
    busy = true
    result = null
    try {
      const text = await exportJson()
      const outcome = await shareOrDownload({
        name: backupFileName(today()),
        mime: MIME.json,
        bytes: new TextEncoder().encode(text),
      })
      if (outcome !== 'cancelled') {
        await setPref('last_file_backup_at', Date.now())
        result = { text: 'Backup file saved', isError: false }
      }
    } catch {
      result = { text: "Couldn't save the file.", isError: true }
    } finally {
      busy = false
    }
  }

  async function pickFile(e: Event) {
    const input = e.currentTarget as HTMLInputElement
    const file = input.files?.[0]
    input.value = ''
    if (!file) return
    result = null
    if (file.size > MAX_BACKUP_FILE_BYTES) {
      result = { text: DAMAGED, isError: true }
      return
    }
    try {
      const text = await file.text()
      const preview = previewJson(text)
      pendingFile = { text, count: preview.expenseCount, date: formatFullDate(dayOf(Date.parse(preview.exportedAt))) }
      confirm = 'file'
    } catch (err) {
      result = { text: err instanceof Error && err.message ? err.message : DAMAGED, isError: true }
    }
  }

  function doRestoreFile() {
    const file = pendingFile
    confirm = null
    pendingFile = null
    if (file) void run('Restored', () => restoreFromFile(file.text))
  }

  const swatch = (id: ThemeId) => {
    const light = palette(id, false)
    const dk = palette(id, true)
    return { background: `linear-gradient(135deg, ${light.bg} 50%, ${dk.bg} 50%)`, accent: dark() ? dk.accent : light.accent }
  }
</script>

<TopBar title="Settings" />
<div class="page">
  <h2>Theme</h2>
  <div class="themes">
    {#each THEMES as t (t.id)}
      {@const s = swatch(t.id)}
      <button
        class="theme"
        aria-pressed={t.id === theme}
        onclick={() => {
          if (t.id !== theme) {
            pendingTheme = t.id
            confirm = 'theme'
          }
        }}
      >
        <span class="swatch" class:on={t.id === theme} style:background={s.background}>
          <span class="dot" style:background={s.accent}></span>
        </span>
        <span class="tname" class:on={t.id === theme}>{t.name}</span>
      </button>
    {/each}
  </div>
  <p class="hint">Light or dark follows your phone.</p>

  <h2>Backup</h2>
  <div class="two">
    <button class="secondary" disabled={busy} onclick={saveFile}>Save backup file</button>
    <button class="outline" disabled={busy} onclick={() => fileInput?.click()}>Restore from file</button>
  </div>
  <input
    bind:this={fileInput}
    class="file"
    type="file"
    accept=".json,application/json,text/plain"
    onchange={pickFile}
    tabindex="-1"
    aria-hidden="true"
  />
  <p class="hint">{fileStatus}</p>

  <label>
    <span>GitHub repo (owner/name)</span>
    <input
      type="text"
      inputmode="url"
      autocapitalize="off"
      autocorrect="off"
      spellcheck="false"
      aria-invalid={repoError !== null}
      bind:value={repo}
      oninput={() => (repoError = null)}
    />
  </label>
  {#if repoError}<p class="error" role="alert">{repoError}</p>{/if}
  <label>
    <span>GitHub token</span>
    <input type="password" autocomplete="off" bind:value={token} />
  </label>
  <p class="hint">
    {hasToken ? 'Token saved. Leave empty to keep it.' : 'Use a fine-grained token with Contents read/write on this repo only.'}
    The token is stored on this phone only, without encryption.
  </p>
  <button class="primary" disabled={busy} onclick={askSave}>Save</button>

  <p class="status" class:error={status.isError}>{status.text}</p>
  <button class="secondary" disabled={!canUse || !enabled || busy} onclick={() => run('Backed up', backupNow)}>Back up now</button>
  {#if result}<p class="result" class:error={result.isError} role="status">{result.text}</p>{/if}

  <button class="outline wide" disabled={!canUse || busy} onclick={() => (confirm = 'restore')}>Restore from GitHub</button>
  {#if !enabled}
    <button class="outline wide" disabled={!canUse || busy} onclick={() => (confirm = 'fresh')}>Start fresh</button>
    <p class="hint">Restore your old data or start fresh to turn on GitHub backups.</p>
  {/if}

  <div class="gap"></div>
  <NavRow title="Voice language" value={voice.name} onclick={() => (pickingVoice = true)} />
  <NavRow title="Labels" onclick={() => navigate('/labels')} />
  <NavRow title="Payment methods" onclick={() => navigate('/methods')} />

  <h2>About the dev</h2>
  <div class="about">
    <span class="avatar">{AboutDev.name.slice(0, 1)}</span>
    <div class="who">
      <strong>{AboutDev.name}</strong>
      <span>{AboutDev.tagline}</span>
    </div>
  </div>
  <div class="two">
    <a class="outline link" href={AboutDev.github} target="_blank" rel="noopener noreferrer">GitHub</a>
    <a class="outline link" href={AboutDev.linkedin} target="_blank" rel="noopener noreferrer">LinkedIn</a>
  </div>
  <p class="hint version">v{__APP_VERSION__}</p>
</div>

<Sheet open={pickingVoice} onClose={() => (pickingVoice = false)}>
  <h2 class="sheet-title">Voice language</h2>
  <div class="voices">
    {#each VOICE_LANGUAGES as l (l.id)}
      <button
        class="voice"
        onclick={() => {
          pickingVoice = false
          if (l.id !== voice.id) {
            pendingVoice = l.id
            confirm = 'voice'
          }
        }}
      >
        <span>{l.name}</span>
        {#if l.id === voice.id}<span class="check">&#10003;</span>{/if}
      </button>
    {/each}
  </div>
</Sheet>

<ConfirmDialog
  open={confirm === 'theme' && pendingTheme !== null}
  message={confirmCopy.changeTheme(THEMES.find((t) => t.id === pendingTheme)?.name ?? '')}
  confirmText="Change"
  onConfirm={doTheme}
  onCancel={() => ((confirm = null), (pendingTheme = null))}
/>
<ConfirmDialog
  open={confirm === 'voice' && pendingVoice !== null}
  message={confirmCopy.changeVoiceLanguage(VOICE_LANGUAGES.find((l) => l.id === pendingVoice)?.name ?? '')}
  confirmText="Change"
  onConfirm={doVoice}
  onCancel={() => ((confirm = null), (pendingVoice = null))}
/>
<ConfirmDialog
  open={confirm === 'save'}
  message={confirmCopy.saveBackupSettings()}
  confirmText="Save"
  onConfirm={doSave}
  onCancel={() => (confirm = null)}
/>
<ConfirmDialog
  open={confirm === 'restore'}
  message={confirmCopy.restore()}
  confirmText="Restore"
  danger
  onConfirm={() => ((confirm = null), void run('Restored', restoreFromGitHub))}
  onCancel={() => (confirm = null)}
/>
<ConfirmDialog
  open={confirm === 'fresh'}
  message={confirmCopy.startFresh()}
  confirmText="Start fresh"
  onConfirm={() => ((confirm = null), void run('Backed up', startFresh))}
  onCancel={() => (confirm = null)}
/>
<ConfirmDialog
  open={confirm === 'file' && pendingFile !== null}
  message={confirmCopy.restoreFile(pendingFile?.count ?? 0, pendingFile?.date ?? '')}
  confirmText="Restore"
  danger
  onConfirm={doRestoreFile}
  onCancel={() => ((confirm = null), (pendingFile = null))}
/>

<style>
  .page {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    padding: 0 16px 24px;
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  h2 {
    margin: 8px 0 0;
    font-size: 13px;
    font-weight: 600;
    color: var(--muted);
  }

  .themes {
    display: flex;
    gap: 4px;
  }

  .theme {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
    border: 0;
    background: none;
    padding: 0;
  }

  .swatch {
    width: 48px;
    height: 48px;
    border-radius: 50%;
    border: 1px solid var(--line);
    display: grid;
    place-items: center;
  }

  .swatch.on {
    border: 3px solid var(--accent);
  }

  .dot {
    width: 14px;
    height: 14px;
    border-radius: 50%;
  }

  .tname {
    font-size: 12px;
    color: var(--muted);
  }

  .tname.on {
    color: var(--fg);
  }

  .hint,
  .status,
  .result {
    margin: 0;
    font-size: 13px;
    color: var(--muted);
  }

  .status {
    font-size: 14px;
  }

  .result {
    font-size: 14px;
    color: var(--fg);
  }

  .error {
    color: var(--danger);
  }

  .two {
    display: flex;
    gap: 8px;
  }

  .two > * {
    flex: 1;
  }

  button,
  .link {
    min-height: 48px;
    border-radius: 12px;
    font-weight: 600;
    font-size: 15px;
  }

  button:disabled {
    opacity: 0.5;
  }

  .primary {
    border: 0;
    background: var(--accent);
    color: var(--on-accent);
  }

  .secondary {
    border: 0;
    background: var(--surface2);
  }

  .outline,
  .link {
    border: 1px solid var(--line);
    background: none;
    display: grid;
    place-items: center;
    text-decoration: none;
    color: var(--fg);
  }

  .wide {
    width: 100%;
  }

  .file {
    position: absolute;
    width: 1px;
    height: 1px;
    opacity: 0;
    pointer-events: none;
  }

  label {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  label span {
    font-size: 12px;
    color: var(--muted);
  }

  label input {
    height: 48px;
    padding: 0 12px;
    border: 1px solid var(--line);
    border-radius: 12px;
    background: var(--surface);
    color: var(--fg);
  }

  label input[aria-invalid='true'] {
    border-color: var(--danger);
  }

  .gap {
    height: 4px;
  }

  .about {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 14px 16px;
    border-radius: 14px;
    background: var(--surface);
  }

  .avatar {
    width: 44px;
    height: 44px;
    border-radius: 50%;
    display: grid;
    place-items: center;
    background: var(--accent);
    color: var(--on-accent);
    font-weight: 700;
    font-size: 18px;
  }

  .who {
    display: flex;
    flex-direction: column;
  }

  .who span {
    color: var(--muted);
    font-size: 14px;
  }

  .version {
    text-align: center;
  }

  .sheet-title {
    margin: 0 0 8px;
    font-size: 18px;
    color: var(--fg);
  }

  .voices {
    display: flex;
    flex-direction: column;
    padding-bottom: 8px;
  }

  .voice {
    display: flex;
    justify-content: space-between;
    border: 0;
    background: none;
    text-align: left;
    padding: 0 4px;
    font-weight: 500;
  }

  .check {
    color: var(--accent);
  }
</style>
