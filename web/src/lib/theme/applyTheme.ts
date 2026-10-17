import { colors, type ThemeId } from './palettes'

let current: ThemeId = 'CLASSIC'
let listening = false

function isDark(): boolean {
  return typeof matchMedia === 'function' && matchMedia('(prefers-color-scheme: dark)').matches
}

/** Sets the theme CSS custom properties on :root and the browser theme color. Re-applies when the phone flips light/dark. */
export function applyTheme(id: ThemeId): void {
  current = id
  const c = colors(id, isDark())
  const root = document.documentElement.style
  root.setProperty('--bg', c.bg)
  root.setProperty('--surface', c.surface)
  root.setProperty('--surface2', c.surface2)
  root.setProperty('--key', c.key)
  root.setProperty('--fg', c.fg)
  root.setProperty('--muted', c.muted)
  root.setProperty('--line', c.line)
  root.setProperty('--accent', c.accent)
  root.setProperty('--on-accent', c.onAccent)
  root.setProperty('--danger', c.danger)
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', c.bg)
  if (!listening && typeof matchMedia === 'function') {
    listening = true
    matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => applyTheme(current))
  }
}
