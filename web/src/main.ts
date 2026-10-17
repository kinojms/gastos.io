import { mount } from 'svelte'
import { registerSW } from 'virtual:pwa-register'
import './app.css'
import App from './App.svelte'
import { startAutoBackup } from './lib/backup/auto'
import { openGastosDb } from './lib/data/db'
import { loadPrefs } from './lib/data/prefs'
import { applyTheme } from './lib/theme/applyTheme'
import type { ThemeId } from './lib/theme/palettes'

async function start() {
  applyTheme('CLASSIC')
  await openGastosDb()
  const prefs = await loadPrefs()
  applyTheme((prefs.theme_id as ThemeId | undefined) ?? 'CLASSIC')
  void navigator.storage?.persist?.()
  startAutoBackup()
  mount(App, { target: document.getElementById('app')! })
  registerSW({ immediate: true })
}

void start()
