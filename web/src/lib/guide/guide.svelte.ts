import { getPref, setPref } from '../data/prefs'
import type { Axis } from '../domain/swipe'

export const GUIDE_TOTAL = 5

/** Bump to show the tour again to everyone who has already seen an earlier version. */
export const GUIDE_VERSION = 1

export interface GuideStep {
  number: number
  /** Copy is verbatim from the Android tour. */
  text: string
  /** Set on steps the user completes by swiping. */
  swipeAxis: Axis | null
  /** `data-guide` values of the elements this step lights up. */
  targets: string[]
  isLast: boolean
}

export const GUIDE_STEPS: GuideStep[] = [
  { number: 1, text: 'Enter the amount of your expense', swipeAxis: null, targets: ['numpad', 'enter'], isLast: false },
  {
    number: 2,
    text: 'You can change which payment method you used for this expense',
    swipeAxis: 'METHOD',
    targets: ['swipe'],
    isLast: false,
  },
  { number: 3, text: 'Then, you can choose from a set of expense types', swipeAxis: 'LABEL', targets: ['swipe'], isLast: false },
  {
    number: 4,
    text: 'You can also specify what you bought (ex: Jollibee or MTG Cards)',
    swipeAxis: null,
    targets: ['description'],
    isLast: false,
  },
  { number: 5, text: 'Finally, keep track of your expenses history here!', swipeAxis: null, targets: ['history'], isLast: true },
]

/**
 * State machine for the Home coach-mark tour. `startIfUnseen` marks the tour seen when it ends;
 * `start` (replay from Settings) never touches the stored flag.
 */
export function createGuide() {
  let index = $state<number | null>(null)
  let auto = false

  function finish() {
    if (index === null) return
    index = null
    if (auto) {
      auto = false
      void setPref('guide_seen_version', GUIDE_VERSION)
    }
  }

  // Leaving the app mid-tour ends it, and an automatic tour then counts as seen.
  if (typeof document !== 'undefined') {
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'hidden') finish()
    })
  }

  return {
    get step(): GuideStep | null {
      return index === null ? null : GUIDE_STEPS[index]
    },

    /** Replay: starts at the first step; finishing does not write the seen flag. */
    start() {
      auto = false
      index = 0
    },

    /** First-run tour: starts only when the stored version is older than GUIDE_VERSION. */
    async startIfUnseen() {
      if (index !== null) return
      const seen = (await getPref('guide_seen_version')) ?? 0
      if (seen < GUIDE_VERSION && index === null) {
        auto = true
        index = 0
      }
    },

    /** Advances past a non-swipe step; on the last step it finishes. Ignored on swipe steps. */
    next() {
      if (index === null || GUIDE_STEPS[index].swipeAxis !== null) return
      if (GUIDE_STEPS[index].isLast) finish()
      else index += 1
    },

    /** A swipe on the current step's axis completes it. Other axes, and steps without a swipe, ignore it. */
    onSwipe(axis: Axis) {
      if (index === null) return
      const axisNeeded = GUIDE_STEPS[index].swipeAxis
      if (axisNeeded === null || axisNeeded !== axis) return
      index += 1
    },

    skip: finish,
    finish,
  }
}

export type Guide = ReturnType<typeof createGuide>

export const guide: Guide = createGuide()
