import { addExpense, dayTotal } from '../lib/data/expenses'
import { onDataChanged } from '../lib/data/events'
import { allLabels } from '../lib/data/labels'
import { activeMethods } from '../lib/data/methods'
import type { Label, PaymentMethod } from '../lib/data/models'
import { getPref, resolveLabel, resolveMethod, setPref } from '../lib/data/prefs'
import { applyKey, toCentavos, type Key } from '../lib/domain/amountInput'
import { toDay } from '../lib/domain/dates'
import { DESCRIPTION_MAX, cleanDescription } from '../lib/domain/expenseText'
import { formatMoney } from '../lib/domain/money'
import { stepIndex, type Axis } from '../lib/domain/swipe'

export interface HomeOptions {
  /** True while the guided tour runs: typing and Enter are ignored so the tour never saves. */
  tourActive?: () => boolean
  now?: () => number
}

/** Home screen state: the amount being typed, the swipe selection and today's total. Port of HomeViewModel.kt. */
export function createHomeState(opts: HomeOptions = {}) {
  const tourActive = opts.tourActive ?? (() => false)
  const now = opts.now ?? Date.now

  let amount = $state('')
  let description = $state('')
  let labels = $state<Label[]>([])
  let methods = $state<PaymentMethod[]>([])
  let label = $state<Label | null>(null)
  let method = $state<PaymentMethod | null>(null)
  let today = $state(toDay(new Date(now())))
  let todayTotal = $state(0)
  /** Bumps whenever state rewrites the description itself (Enter), so the input can resync. */
  let descriptionResets = $state(0)

  /** Serialises swipes so each steps from the previous one's saved result. */
  let swipeChain: Promise<unknown> = Promise.resolve()

  async function refresh(): Promise<void> {
    const [allL, active] = await Promise.all([allLabels(), activeMethods()])
    labels = allL
    methods = active
    label = allL.length ? resolveLabel(allL, await getPref('last_label_id')) : null
    method = active.length ? resolveMethod(active, await getPref('last_payment_method_id')) : null
    today = toDay(new Date(now()))
    todayTotal = await dayTotal(today)
  }

  function key(k: Key): void {
    if (tourActive()) return
    amount = applyKey(amount, k)
  }

  function setDescription(text: string): void {
    description = text.slice(0, DESCRIPTION_MAX)
  }

  /** Voice result replaces the box text (trimmed, cut to the max length). */
  function setDescriptionFromVoice(text: string): void {
    const cleaned = cleanDescription(text)
    if (cleaned === null) return
    description = cleaned
    descriptionResets++
  }

  function swipe(axis: Axis, delta: number): Promise<void> {
    const run = swipeChain.then(async () => {
      if (axis === 'METHOD') {
        const active = await activeMethods()
        if (!active.length) return
        const current = resolveMethod(active, await getPref('last_payment_method_id'))
        const next = active[stepIndex(active.length, active.indexOf(current), delta)]
        if (next.id !== current.id) await setPref('last_payment_method_id', next.id)
        methods = active
        method = next
      } else if (axis === 'LABEL') {
        const all = await allLabels()
        if (!all.length) return
        const current = resolveLabel(all, await getPref('last_label_id'))
        const next = all[stepIndex(all.length, all.indexOf(current), delta)]
        if (next.id !== current.id) await setPref('last_label_id', next.id)
        labels = all
        label = next
      }
    })
    swipeChain = run.catch(() => undefined)
    return run
  }

  /** Saves the typed amount. Returns the toast text, or null when there is nothing to save. */
  async function enter(): Promise<string | null> {
    if (tourActive()) return null
    const cents = toCentavos(amount)
    if (cents === null || !label || !method) return null
    const typed = amount
    const typedDescription = description
    const chosenLabel = label
    const chosenMethod = method
    // Cleared before the insert so a double tap cannot save twice.
    amount = ''
    description = ''
    descriptionResets++
    try {
      const desc = cleanDescription(typedDescription)
      await addExpense(cents, chosenLabel.id, chosenMethod.id, desc, now())
      return `Added ${formatMoney(cents)} · ${desc ?? chosenLabel.name} · ${chosenMethod.name}`
    } catch {
      // Give the text back unless the user started typing again.
      if (amount === '') amount = typed
      if (description === '') {
        description = typedDescription
        descriptionResets++
      }
      return "Couldn't save. Try again."
    }
  }

  /** Loads everything, then keeps today's date and total fresh. Returns a stop function. */
  function start(periodMs = 15_000): () => void {
    void refresh()
    const off = onDataChanged(() => void refresh())
    const timer = setInterval(() => {
      if (toDay(new Date(now())) !== today) void refresh()
    }, periodMs)
    return () => {
      off()
      clearInterval(timer)
    }
  }

  return {
    get amount() { return amount },
    get description() { return description },
    get labels() { return labels },
    get methods() { return methods },
    get label() { return label },
    get method() { return method },
    get today() { return today },
    get todayTotal() { return todayTotal },
    get descriptionResets() { return descriptionResets },
    refresh,
    start,
    key,
    swipe,
    enter,
    setDescription,
    setDescriptionFromVoice,
  }
}

export type HomeState = ReturnType<typeof createHomeState>
