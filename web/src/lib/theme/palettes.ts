export type ThemeId = 'CLASSIC' | 'SAND' | 'NORD' | 'MOCHA' | 'FOREST'

export const THEMES: { id: ThemeId; name: string }[] = [
  { id: 'CLASSIC', name: 'Classic' },
  { id: 'SAND', name: 'Sand' },
  { id: 'NORD', name: 'Nord' },
  { id: 'MOCHA', name: 'Mocha' },
  { id: 'FOREST', name: 'Forest' },
]

export interface Palette {
  bg: string
  surface: string
  fg: string
  muted: string
  line: string
  accent: string
  onAccent: string
}

/** Every token the UI uses; mirrors Android's GastosColors. */
export interface Colors extends Palette {
  surface2: string
  key: string
  danger: string
}

export const DANGER = { light: '#C2412D', dark: '#EF6B55' }

const p = (bg: string, surface: string, fg: string, muted: string, line: string, accent: string, onAccent: string): Palette => ({
  bg, surface, fg, muted, line, accent, onAccent,
})

// Copied from ui/theme/Palettes.kt (it carries contrast fixes the v1.1 spec table lacks).
const PALETTES: Record<ThemeId, { light: Palette; dark: Palette }> = {
  CLASSIC: {
    light: p('#EEF1F4', '#FFFFFF', '#16202B', '#5D6B7A', '#DDE3EA', '#E8A317', '#1C1300'),
    dark: p('#0B1016', '#1C2632', '#E8EDF2', '#92A0AE', '#2A3542', '#F2B33A', '#1C1300'),
  },
  SAND: {
    light: p('#FDF6E3', '#FFFBF0', '#073642', '#5F747C', '#E8E1CC', '#8F6C00', '#FDF6E3'),
    dark: p('#002B36', '#073642', '#EEE8D5', '#93A1A1', '#0E4B5A', '#B58900', '#002B36'),
  },
  NORD: {
    light: p('#ECEFF4', '#FFFFFF', '#2E3440', '#4C566A', '#D8DEE9', '#5378A3', '#FFFFFF'),
    dark: p('#2E3440', '#3B4252', '#ECEFF4', '#D8DEE9', '#434C5E', '#88C0D0', '#2E3440'),
  },
  MOCHA: {
    light: p('#EFF1F5', '#FFFFFF', '#4C4F69', '#696C82', '#DCE0E8', '#8839EF', '#FFFFFF'),
    dark: p('#1E1E2E', '#313244', '#CDD6F4', '#A6ADC8', '#45475A', '#CBA6F7', '#1E1E2E'),
  },
  FOREST: {
    light: p('#FBF1C7', '#F9F5D7', '#3C3836', '#776A60', '#EBDBB2', '#74700D', '#FBF1C7'),
    dark: p('#282828', '#32302F', '#EBDBB2', '#A89984', '#504945', '#B8BB26', '#282828'),
  },
}

export function palette(id: ThemeId, dark: boolean): Palette {
  return PALETTES[id][dark ? 'dark' : 'light']
}

const toRgb = (hex: string): [number, number, number] => [
  parseInt(hex.slice(1, 3), 16),
  parseInt(hex.slice(3, 5), 16),
  parseInt(hex.slice(5, 7), 16),
]

const toHex = (rgb: number[]): string =>
  '#' + rgb.map((v) => Math.round(v).toString(16).padStart(2, '0')).join('').toUpperCase()

function lerpHex(a: string, b: string, t: number): string {
  const [ar, ag, ab] = toRgb(a)
  const [br, bg, bb] = toRgb(b)
  return toHex([ar + (br - ar) * t, ag + (bg - ag) * t, ab + (bb - ab) * t])
}

/** Port of GastosColors.from: Classic has hand-tuned surfaces, the rest derive them. */
export function colors(id: ThemeId, dark: boolean): Colors {
  const danger = dark ? DANGER.dark : DANGER.light
  if (id === 'CLASSIC') {
    return dark
      ? { ...palette(id, true), surface: '#151D26', surface2: '#1C2632', key: '#222D3A', danger }
      : { ...palette(id, false), surface2: '#F3F5F8', key: '#E9EDF2', danger }
  }
  const base = palette(id, dark)
  return { ...base, surface2: lerpHex(base.bg, base.surface, 0.5), key: base.surface, danger }
}

function channel(v: number): number {
  const c = v / 255
  return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4)
}

function luminance(hex: string): number {
  const [r, g, b] = toRgb(hex)
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
}

/** WCAG 2.x contrast ratio between two opaque hex colors. */
export function contrastRatio(a: string, b: string): number {
  const la = luminance(a)
  const lb = luminance(b)
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05)
}
