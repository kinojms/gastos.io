import { beforeEach, describe, expect, it } from 'vitest'
import { openGastosDb } from '../lib/data/db'
import { rowsForDay } from '../lib/data/expenses'
import { getPref } from '../lib/data/prefs'
import { toDay } from '../lib/domain/dates'
import { createHomeState } from './homeState.svelte'

const NOON = Date.parse('2026-10-16T04:00:00Z') // Oct 16 12:00 Manila
const DAY = toDay(new Date(NOON))

let n = 0
beforeEach(async () => {
  await openGastosDb('h' + n++)
})

async function ready(touring = false) {
  const home = createHomeState({ now: () => NOON, tourActive: () => touring })
  await home.refresh()
  return home
}

function type(home: Awaited<ReturnType<typeof ready>>, text: string) {
  for (const ch of text) home.key(ch === '.' ? 'dot' : { digit: Number(ch) })
}

describe('enter', () => {
  it('saves nothing for an empty or zero amount', async () => {
    const home = await ready()
    expect(await home.enter()).toBeNull()
    type(home, '0')
    expect(await home.enter()).toBeNull()
    expect(await rowsForDay(DAY)).toEqual([])
  })

  it('saves amount, label and method, then clears the inputs but keeps the selection', async () => {
    const home = await ready()
    await home.swipe('METHOD', 1) // GCash
    type(home, '45.5')
    home.setDescription('  Jollibee ')
    expect(await home.enter()).toBe('Added ₱45.50 · Jollibee · GCash')
    expect(home.amount).toBe('')
    expect(home.description).toBe('')
    expect(home.method?.name).toBe('GCash')
    expect(home.label?.name).toBe('Food')
    const [row] = await rowsForDay(DAY)
    expect(row).toMatchObject({ amountCentavos: 4550, description: 'Jollibee', paymentMethodName: 'GCash' })
  })

  it('names the label in the toast when there is no description', async () => {
    const home = await ready()
    type(home, '185')
    expect(await home.enter()).toBe('Added ₱185.00 · Food · Cash')
  })

  it('two rapid Enters save once', async () => {
    const home = await ready()
    type(home, '1')
    const results = await Promise.all([home.enter(), home.enter()])
    expect(results.filter((r) => r !== null)).toHaveLength(1)
    expect(await rowsForDay(DAY)).toHaveLength(1)
  })

  it('ignores keys and Enter while the tour runs', async () => {
    const home = await ready(true)
    type(home, '5')
    expect(home.amount).toBe('')
    expect(await home.enter()).toBeNull()
  })

  it('updates today and the total after saving', async () => {
    const home = await ready()
    home.start()
    type(home, '12')
    await home.enter()
    await new Promise<void>((r) => setTimeout(r, 50))
    expect(home.todayTotal).toBe(1200)
    expect(home.today).toBe(DAY)
  })
})

describe('swipes', () => {
  it('wraps the payment method around and remembers it', async () => {
    const home = await ready()
    expect(home.method?.name).toBe('Cash')
    await home.swipe('METHOD', -1)
    expect(home.method?.name).toBe('BDO')
    expect(await getPref('last_payment_method_id')).toBe(home.method?.id)
    await home.swipe('METHOD', 1)
    expect(home.method?.name).toBe('Cash')
  })

  it('wraps the label around and remembers it', async () => {
    const home = await ready()
    await home.swipe('LABEL', -1)
    expect(home.label?.name).toBe('Other')
    expect(await getPref('last_label_id')).toBe(home.label?.id)
  })

  it('steps once per swipe even when swipes are fired back to back', async () => {
    const home = await ready()
    await Promise.all([home.swipe('METHOD', 1), home.swipe('METHOD', 1)])
    expect(home.method?.name).toBe('Maribank')
  })

  it('starts on the last-used selection', async () => {
    const first = await ready()
    await first.swipe('METHOD', 2)
    await first.swipe('LABEL', 1)
    const second = await ready()
    expect(second.method?.name).toBe('Maribank')
    expect(second.label?.name).toBe('Transpo')
  })
})

describe('description', () => {
  it('is cut at 60 characters', async () => {
    const home = await ready()
    home.setDescription('x'.repeat(80))
    expect(home.description).toHaveLength(60)
  })

  it('voice text replaces it, trimmed', async () => {
    const home = await ready()
    home.setDescription('old')
    home.setDescriptionFromVoice('  MTG cards ')
    expect(home.description).toBe('MTG cards')
    home.setDescriptionFromVoice('   ')
    expect(home.description).toBe('MTG cards')
  })
})
