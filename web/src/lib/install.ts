export type InstallState = 'installed' | 'ios-safari' | 'other'

export const BANNER_SNOOZE_DAYS = 30
const DAY_MS = 86_400_000

/** Android APK releases, for people who land here from an Android phone. */
export const RELEASES_URL = 'https://github.com/kinojms/gastos.io/releases'

/**
 * Whether the app runs from the Home Screen, in iPhone/iPad Safari (so it can be installed), or elsewhere.
 * iPadOS Safari reports itself as a Mac, so a Mac with touch points counts as iOS.
 */
export function installState(
  nav: { userAgent: string; maxTouchPoints?: number; standalone?: boolean } = navigator as never,
  mm: (query: string) => { matches: boolean } = (q) => matchMedia(q),
): InstallState {
  if (nav.standalone === true || mm('(display-mode: standalone)').matches) return 'installed'
  const ios = /iPhone|iPad|iPod/.test(nav.userAgent) || (/Macintosh/.test(nav.userAgent) && (nav.maxTouchPoints ?? 0) > 1)
  return ios ? 'ios-safari' : 'other'
}

/** Hidden once installed, and for 30 days after the user dismisses it. */
export function shouldShowInstallBanner(state: InstallState, dismissedAt: number | undefined, now: number): boolean {
  if (state === 'installed') return false
  return dismissedAt === undefined || now - dismissedAt >= BANNER_SNOOZE_DAYS * DAY_MS
}
