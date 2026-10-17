import { beforeEach, describe, expect, it, vi } from 'vitest'
import { openGastosDb } from './db'
import { onDataChanged } from './events'
import {
  addExpense, countByLabel, daySummaries, dayTotal, deleteExpense, monthByLabel, monthByMethod,
  oldestTimestamp, rowsForDay, rowsForMonth, updateExpense,
} from './expenses'
import { addLabel, allLabels, deleteLabel, reorderLabels, validateLabelName } from './labels'
import { activeMethods, addMethod, allMethods, archiveMethod, validateMethodName } from './methods'
import { getPref, loadPrefs, resolveMethod, setPref } from './prefs'

const MANILA_2359 = Date.parse('2026-10-16T15:59:00Z') // Oct 16 23:59 in Manila
const MANILA_0000 = Date.parse('2026-10-16T16:00:00Z') // Oct 17 00:00 in Manila

let n = 0
beforeEach(async () => {
  await openGastosDb('t' + n++)
})

async function ids() {
  const labels = await allLabels()
  const methods = await allMethods()
  return {
    food: labels[0].id,
    hobbies: labels[2].id,
    other: labels[4].id,
    cash: methods[0].id,
    gcash: methods[1].id,
  }
}

describe('seed', () => {
  it('starts with 5 labels (Other last) and 6 methods, Cash first', async () => {
    const labels = await allLabels()
    expect(labels.map((l) => l.name)).toEqual(['Food', 'Transpo', 'Hobbies', 'Bills', 'Other'])
    expect(labels[4].isOther).toBe(true)
    const methods = await allMethods()
    expect(methods.map((m) => m.name)).toEqual(['Cash', 'GCash', 'Maribank', 'BPI', 'Maya', 'BDO'])
    expect(methods[0].colorArgb).toBe(-8945525)
  })
})

describe('expenses', () => {
  it('adds and reads a row with names', async () => {
    const { food, cash } = await ids()
    await addExpense(4550, food, cash, '  Jollibee ', MANILA_2359)
    const rows = await rowsForDay('2026-10-16')
    expect(rows).toHaveLength(1)
    expect(rows[0]).toMatchObject({
      amountCentavos: 4550, description: 'Jollibee', labelName: 'Food', paymentMethodName: 'Cash', notes: null,
    })
  })

  it('keeps 23:59 on its day and midnight on the next', async () => {
    const { food, cash } = await ids()
    await addExpense(100, food, cash, null, MANILA_2359)
    await addExpense(200, food, cash, null, MANILA_0000)
    expect((await rowsForDay('2026-10-16')).map((r) => r.amountCentavos)).toEqual([100])
    expect((await rowsForDay('2026-10-17')).map((r) => r.amountCentavos)).toEqual([200])
    expect(await dayTotal('2026-10-17')).toBe(200)
  })

  it('lists a day newest first and a month oldest first', async () => {
    const { food, cash } = await ids()
    await addExpense(1, food, cash, null, Date.parse('2026-10-16T01:00:00Z'))
    await addExpense(2, food, cash, null, Date.parse('2026-10-16T02:00:00Z'))
    expect((await rowsForDay('2026-10-16')).map((r) => r.amountCentavos)).toEqual([2, 1])
    expect((await rowsForMonth('2026-10')).map((r) => r.amountCentavos)).toEqual([1, 2])
  })

  it('groups a month into day summaries, newest first', async () => {
    const { food, cash } = await ids()
    await addExpense(100, food, cash, null, MANILA_2359)
    await addExpense(250, food, cash, null, MANILA_2359 - 1000)
    await addExpense(300, food, cash, null, MANILA_0000)
    expect(await daySummaries('2026-10')).toEqual([
      { date: '2026-10-17', count: 1, totalCentavos: 300 },
      { date: '2026-10-16', count: 2, totalCentavos: 350 },
    ])
  })

  it('totals by label and by method, largest first, archived methods included', async () => {
    const { food, hobbies, cash, gcash } = await ids()
    await addExpense(100, food, cash, null, MANILA_2359)
    await addExpense(900, hobbies, gcash, null, MANILA_2359)
    await archiveMethod(gcash)
    expect((await monthByLabel('2026-10')).map((t) => [t.name, t.totalCentavos])).toEqual([['Hobbies', 900], ['Food', 100]])
    expect((await monthByMethod('2026-10')).map((t) => [t.name, t.totalCentavos])).toEqual([['GCash', 900], ['Cash', 100]])
    expect(await monthByMethod('2026-09')).toEqual([])
  })

  it('updates and deletes, cleaning text', async () => {
    const { food, other, cash } = await ids()
    const id = await addExpense(100, food, cash, null, MANILA_2359)
    await updateExpense(id, {
      amountCentavos: 250, labelId: other, paymentMethodId: cash, timestamp: MANILA_0000,
      description: '  lunch ', notes: '   ',
    })
    const [row] = await rowsForDay('2026-10-17')
    expect(row).toMatchObject({ amountCentavos: 250, labelName: 'Other', description: 'lunch', notes: null })
    await deleteExpense(id)
    expect(await rowsForDay('2026-10-17')).toEqual([])
  })

  it('reports the oldest timestamp', async () => {
    const { food, cash } = await ids()
    expect(await oldestTimestamp()).toBeNull()
    await addExpense(1, food, cash, null, 5000)
    await addExpense(1, food, cash, null, 3000)
    expect(await oldestTimestamp()).toBe(3000)
  })

  it('fires the data-changed callback once per write', async () => {
    const { food, cash } = await ids()
    const cb = vi.fn()
    const off = onDataChanged(cb)
    const id = await addExpense(1, food, cash)
    expect(cb).toHaveBeenCalledTimes(1)
    await deleteExpense(id)
    expect(cb).toHaveBeenCalledTimes(2)
    off()
  })
})

describe('labels', () => {
  it('moves expenses to Other when a label is deleted, and refuses to delete Other', async () => {
    const { hobbies, other, cash } = await ids()
    await addExpense(100, hobbies, cash, null, MANILA_2359)
    await addExpense(200, hobbies, cash, null, MANILA_2359)
    expect(await countByLabel(hobbies)).toBe(2)
    await deleteLabel(hobbies)
    expect((await allLabels()).map((l) => l.name)).not.toContain('Hobbies')
    expect(await countByLabel(other)).toBe(2)
    await expect(deleteLabel(other)).rejects.toThrow('Other')
  })

  it('adds at the end, reorders, and validates names', async () => {
    const labels = await allLabels()
    const id = await addLabel('  Coffee ', 1)
    expect((await allLabels()).at(-1)).toMatchObject({ id, name: 'Coffee', position: 5, isOther: false })
    await reorderLabels([id, ...labels.map((l) => l.id)])
    expect((await allLabels())[0].name).toBe('Coffee')
    expect(validateLabelName(await allLabels(), 'food', null)).toBe('That label already exists')
    expect(validateLabelName(await allLabels(), '  ', null)).toBe("Name can't be empty")
    expect(validateLabelName(await allLabels(), 'Food', labels[0].id)).toBeNull()
  })
})

describe('methods', () => {
  it('archives but keeps history, and refuses to archive the last active one', async () => {
    const all = await allMethods()
    for (const m of all.slice(1)) await archiveMethod(m.id)
    expect((await activeMethods()).map((m) => m.name)).toEqual(['Cash'])
    await expect(archiveMethod(all[0].id)).rejects.toThrow('At least one payment method')
    expect(await activeMethods()).toHaveLength(1)
    expect(await allMethods()).toHaveLength(6)
  })

  it('validates names among active methods only', async () => {
    const methods = await allMethods()
    expect(validateMethodName(methods, 'gcash', null)).toBe('That payment method already exists')
    await archiveMethod(methods[1].id)
    expect(validateMethodName(await allMethods(), 'gcash', null)).toBeNull()
    const id = await addMethod(' Wise ', 7)
    expect((await allMethods()).at(-1)).toMatchObject({ id, name: 'Wise', archived: false })
  })
})

describe('prefs', () => {
  it('stores values and resolves the last-used method', async () => {
    await setPref('theme_id', 'NORD')
    expect(await getPref('theme_id')).toBe('NORD')
    expect((await loadPrefs()).theme_id).toBe('NORD')
    const active = await activeMethods()
    expect(resolveMethod(active, active[2].id).name).toBe('Maribank')
    expect(resolveMethod(active, 9999).name).toBe('Cash')
  })
})
