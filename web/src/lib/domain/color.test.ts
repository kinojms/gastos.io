import { describe, expect, it } from 'vitest'
import { argbToCss, blend, cssToArgb } from './color'

describe('color', () => {
  it('reads the negative ARGB ints Android writes', () => {
    expect(argbToCss(-8945525)).toBe('#77808b')
    expect(argbToCss(0xff007dfe | 0)).toBe('#007dfe')
  })

  it('writes signed ARGB with alpha FF', () => {
    expect(cssToArgb('#77808B')).toBe(-8945525)
    expect(cssToArgb('#007DFE')).toBe(0xff007dfe | 0)
  })

  it('round trips', () => expect(argbToCss(cssToArgb('#FF6A13'))).toBe('#ff6a13'))

  it('blends over a background', () => {
    expect(blend('#ffffff', '#000000', 0.2)).toBe('#333333')
    expect(blend('#ff0000', '#000000', 0)).toBe('#000000')
    expect(blend('#ff0000', '#000000', 1)).toBe('#ff0000')
  })
})
