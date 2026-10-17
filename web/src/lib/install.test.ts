import { describe, expect, it } from 'vitest'
import { installState, shouldShowInstallBanner } from './install'

const IPHONE = 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1'
const ANDROID = 'Mozilla/5.0 (Linux; Android 14; SM-S921B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36'
const IPAD = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15'
const browser = (matches = false) => () => ({ matches })

describe('installState', () => {
  it('is installed when launched from the Home Screen', () => {
    expect(installState({ userAgent: IPHONE, standalone: true }, browser())).toBe('installed')
    expect(installState({ userAgent: IPHONE }, browser(true))).toBe('installed')
  })

  it('is ios-safari on an iPhone browser tab', () => {
    expect(installState({ userAgent: IPHONE, standalone: false }, browser())).toBe('ios-safari')
  })

  it('treats an iPad that reports as a Mac as iOS, but not a real Mac', () => {
    expect(installState({ userAgent: IPAD, maxTouchPoints: 5 }, browser())).toBe('ios-safari')
    expect(installState({ userAgent: IPAD, maxTouchPoints: 0 }, browser())).toBe('other')
  })

  it('is other on Android Chrome', () => {
    expect(installState({ userAgent: ANDROID }, browser())).toBe('other')
  })
})

describe('shouldShowInstallBanner', () => {
  const day = 86_400_000
  const now = 100 * day

  it('is hidden once installed', () => expect(shouldShowInstallBanner('installed', undefined, now)).toBe(false))
  it('shows when never dismissed', () => expect(shouldShowInstallBanner('ios-safari', undefined, now)).toBe(true))
  it('stays hidden 29 days after dismissal', () => expect(shouldShowInstallBanner('ios-safari', now - 29 * day, now)).toBe(false))
  it('shows again 31 days after dismissal', () => expect(shouldShowInstallBanner('ios-safari', now - 31 * day, now)).toBe(true))
  it('also applies to the non-iPhone note', () => expect(shouldShowInstallBanner('other', now - 29 * day, now)).toBe(false))
})
