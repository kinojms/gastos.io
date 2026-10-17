import { beforeEach, describe, expect, it } from 'vitest'
import { openGastosDb } from '../data/db'
import { getPref, setPref } from '../data/prefs'
import { GUIDE_STEPS, GUIDE_TOTAL, GUIDE_VERSION, createGuide } from './guide.svelte'

let n = 0
beforeEach(async () => {
  await openGastosDb('g' + n++)
})

const settle = () => new Promise<void>((r) => setTimeout(r, 20))

describe('steps', () => {
  it('has the six steps with the Android copy', () => {
    expect(GUIDE_STEPS.map((s) => s.text)).toEqual([
      'Enter the amount of your expense',
      'You can change which payment method you used for this expense',
      'Then, you can choose from a set of expense types',
      'You can also specify what you bought (ex: Jollibee or MTG Cards)',
      'Finally, keep track of your expenses history here!',
      'In your history, swipe an expense left to edit or delete it',
    ])
    expect(GUIDE_STEPS.map((s) => s.swipeAxis)).toEqual([null, 'METHOD', 'LABEL', null, null, null])
    expect(GUIDE_STEPS.map((s) => s.targets)).toEqual([
      ['numpad', 'enter'], ['swipe'], ['swipe'], ['description'], ['history'], ['history'],
    ])
    expect(GUIDE_STEPS.map((s) => s.isLast)).toEqual([false, false, false, false, false, true])
    expect(GUIDE_TOTAL).toBe(6)
    expect(GUIDE_VERSION).toBe(2)
  })
})

describe('guide', () => {
  it('startIfUnseen starts only when the stored version is older', async () => {
    const g = createGuide()
    await g.startIfUnseen()
    expect(g.step?.number).toBe(1)

    await setPref('guide_seen_version', GUIDE_VERSION)
    const again = createGuide()
    await again.startIfUnseen()
    expect(again.step).toBeNull()
  })

  it('people who saw version 1 get only the new step, then count as seen', async () => {
    await setPref('guide_seen_version', 1)
    const g = createGuide()
    await g.startIfUnseen()
    expect(g.step?.number).toBe(6)
    g.next()
    expect(g.step).toBeNull()
    await settle()
    expect(await getPref('guide_seen_version')).toBe(2)
  })

  it('replay always starts at step 1', async () => {
    await setPref('guide_seen_version', 2)
    const g = createGuide()
    g.start()
    expect(g.step?.number).toBe(1)
  })

  it('a swipe on the wrong axis does nothing, the right one advances', () => {
    const g = createGuide()
    g.start()
    g.next() // step 2 (METHOD)
    expect(g.step?.number).toBe(2)
    g.onSwipe('LABEL')
    expect(g.step?.number).toBe(2)
    g.onSwipe('METHOD')
    expect(g.step?.number).toBe(3)
    g.onSwipe('METHOD')
    expect(g.step?.number).toBe(3)
    g.onSwipe('LABEL')
    expect(g.step?.number).toBe(4)
  })

  it('Next is ignored on swipe steps and Done finishes the last step', () => {
    const g = createGuide()
    g.start()
    g.next()
    g.next() // ignored: step 2 needs a swipe
    expect(g.step?.number).toBe(2)
    g.onSwipe('METHOD')
    g.onSwipe('LABEL')
    g.next() // 4 -> 5
    expect(g.step?.number).toBe(5)
    g.next() // 5 -> 6
    expect(g.step?.number).toBe(6)
    g.next()
    expect(g.step).toBeNull()
  })

  it('skip on an automatic tour marks it seen', async () => {
    const g = createGuide()
    await g.startIfUnseen()
    g.skip()
    expect(g.step).toBeNull()
    await settle()
    expect(await getPref('guide_seen_version')).toBe(GUIDE_VERSION)
  })

  it('a replay never writes the seen flag', async () => {
    const g = createGuide()
    g.start()
    g.skip()
    await settle()
    expect(await getPref('guide_seen_version')).toBeUndefined()
  })

  it('hiding the page ends the tour and marks an automatic one seen', async () => {
    const g = createGuide()
    await g.startIfUnseen()
    Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true })
    document.dispatchEvent(new Event('visibilitychange'))
    Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true })
    expect(g.step).toBeNull()
    await settle()
    expect(await getPref('guide_seen_version')).toBe(GUIDE_VERSION)
  })
})
