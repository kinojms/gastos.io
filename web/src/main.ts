import { mount } from 'svelte'
import { registerSW } from 'virtual:pwa-register'
import './app.css'
import App from './App.svelte'
import { startAutoBackup } from './lib/backup/auto'
import { openGastosDb } from './lib/data/db'
import { loadPrefs, setPref } from './lib/data/prefs'
import { applyTheme } from './lib/theme/applyTheme'
import type { ThemeId } from './lib/theme/palettes'
import { showToast } from './lib/ui/toastState.svelte'

async function start() {
  applyTheme('CLASSIC')
  await openGastosDb()
  const prefs = await loadPrefs()
  applyTheme((prefs.theme_id as ThemeId | undefined) ?? 'CLASSIC')
  void navigator.storage?.persist?.()
  startAutoBackup()
  mount(App, { target: document.getElementById('app')! })
  registerSW({ immediate: true })
  // After an update, say so once. A first-ever launch has nothing to compare against.
  if (prefs.last_seen_version !== __APP_VERSION__) {
    if (prefs.last_seen_version !== undefined) showToast(`Updated to v${__APP_VERSION__}`)
    await setPref('last_seen_version', __APP_VERSION__)
  }
}

void start()
