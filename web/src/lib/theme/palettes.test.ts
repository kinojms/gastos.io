import { describe, expect, it } from 'vitest'
import { THEMES, colors, contrastRatio, palette, type ThemeId } from './palettes'

describe('palettes', () => {
  it('copies values from the Android Palettes.kt', () => {
    expect(palette('CLASSIC', false).bg).toBe('#EEF1F4')
    expect(palette('NORD', true).accent).toBe('#88C0D0')
    expect(palette('SAND', false).accent).toBe('#8F6C00')
  })

  it('lists the five themes in Android order', () => {
    expect(THEMES.map((t) => t.id)).toEqual(['CLASSIC', 'SAND', 'NORD', 'MOCHA', 'FOREST'])
    expect(THEMES.map((t) => t.name)).toEqual(['Classic', 'Sand', 'Nord', 'Mocha', 'Forest'])
  })

  it('Classic uses its own surface tokens like GastosColors', () => {
    expect(colors('CLASSIC', true).surface).toBe('#151D26')
    expect(colors('CLASSIC', true).surface2).toBe('#1C2632')
    expect(colors('CLASSIC', false).key).toBe('#E9EDF2')
    expect(colors('NORD', false).key).toBe('#FFFFFF')
    expect(colors('NORD', true).danger).toBe('#EF6B55')
    expect(colors('NORD', false).danger).toBe('#C2412D')
  })

  const ids = THEMES.map((t) => t.id) as ThemeId[]
  for (const id of ids) {
    for (const dark of [false, true]) {
      it(`${id} ${dark ? 'dark' : 'light'} text meets 4.5:1`, () => {
        const c = colors(id, dark)
        expect(contrastRatio(c.fg, c.bg)).toBeGreaterThanOrEqual(4.5)
        expect(contrastRatio(c.onAccent, c.accent)).toBeGreaterThanOrEqual(4.5)
        expect(contrastRatio(c.muted, c.bg)).toBeGreaterThanOrEqual(4.5)
      })
    }
  }
})
