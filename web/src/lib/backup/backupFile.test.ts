import { beforeEach, describe, expect, it } from 'vitest'
import v1 from './fixtures/android-v1.json?raw'
import v2 from './fixtures/android-v2.json?raw'
import { openGastosDb } from '../data/db'
import { addExpense, allExpenses } from '../data/expenses'
import { allLabels } from '../data/labels'
import { allMethods } from '../data/methods'
import { DAMAGED, NEWER, buildBackup, encodeBackup, exportJson, parseBackup, previewJson, restoreJson } from './backupFile'

const withVersion = (json: string, patch: (o: any) => void) => {
  const o = JSON.parse(json)
  patch(o)
  return JSON.stringify(o)
}

describe('parseBackup', () => {
  it('keeps ids and colors of an Android schema 2 file', () => {
    const p = parseBackup(v2)
    expect(p.labels.map((l) => l.id)).toEqual([10, 12, 11])
    expect(p.labels[2]).toMatchObject({ name: 'Other', isOther: true, colorArgb: -8945525 })
    expect(p.methods.map((m) => [m.id, m.archived])).toEqual([[1, false], [2, false], [7, true]])
    expect(p.expenses[0]).toMatchObject({ id: 40, paymentMethodId: 2, description: 'Jollibee', notes: 'With "friends",\nlate' })
    expect(p.expenses[1]).toMatchObject({ description: null, notes: null })
    expect(p.exportedAt).toBe('2026-10-16T04:00:00Z')
  })

  it('turns a schema 1 file into the six default methods with every expense on Cash', () => {
    const p = parseBackup(v1)
    expect(p.methods.map((m) => m.name)).toEqual(['Cash', 'GCash', 'Maribank', 'BPI', 'Maya', 'BDO'])
    expect(p.methods.map((m) => m.id)).toEqual([1, 2, 3, 4, 5, 6])
    expect(p.expenses.every((e) => e.paymentMethodId === 1)).toBe(true)
    expect(p.expenses.every((e) => e.description === null && e.notes === null)).toBe(true)
  })

  it('ignores unknown keys', () => {
    expect(() => parseBackup(withVersion(v2, (o) => (o.extra = 1)))).not.toThrow()
  })

  it('survives an encode round trip', () => {
    const p = parseBackup(v2)
    const text = encodeBackup(buildBackup(p.labels, p.methods, p.expenses, new Date('2026-10-16T04:00:00Z')))
    expect(JSON.parse(text)).toEqual(JSON.parse(v2))
    expect(JSON.parse(text).schemaVersion).toBe(2)
  })

  it('refuses damaged files with the Android message', () => {
    expect(DAMAGED).toBe("This backup file is damaged or isn't a Gastos.io backup.")
    const bad = [
      'not json',
      '',
      '{}',
      '[1,2]',
      '\u0089PNG binary',
      withVersion(v2, (o) => o.labels.push({ id: 99, name: 'Other2', colorArgb: 1, position: 3, isOther: true })),
      withVersion(v2, (o) => (o.expenses[0].labelId = 12345)),
      withVersion(v2, (o) => (o.expenses[0].paymentMethodId = 99)),
      withVersion(v2, (o) => delete o.expenses[0].paymentMethodId),
      withVersion(v2, (o) => (o.expenses[1].id = 40)),
      withVersion(v2, (o) => (o.expenses[0].id = 0)),
      withVersion(v2, (o) => o.paymentMethods.forEach((m: any) => (m.archived = true))),
      withVersion(v2, (o) => (o.paymentMethods[1].id = 1)),
    ]
    for (const text of bad) expect(() => parseBackup(text), text.slice(0, 40)).toThrow(DAMAGED)
  })

  it('refuses a newer schema', () => {
    expect(NEWER).toBe('This backup is from a newer version of Gastos.io. Update the app first.')
    expect(() => parseBackup('{"schemaVersion":3}')).toThrow(NEWER)
  })
})

describe('previewJson', () => {
  it('counts expenses and reads the export time', () => {
    expect(previewJson(v2)).toEqual({ expenseCount: 3, exportedAt: '2026-10-16T04:00:00Z' })
  })
})

let n = 0
beforeEach(async () => {
  await openGastosDb('b' + n++)
})

describe('restore', () => {
  it('replaces existing data, then new ids do not collide', async () => {
    const labels = await allLabels()
    const methods = await allMethods()
    await addExpense(999, labels[0].id, methods[0].id)

    const result = await restoreJson(v2)
    expect(result.expenseCount).toBe(3)
    expect((await allExpenses()).map((e) => e.id)).toEqual([40, 41, 42])
    expect((await allLabels()).map((l) => l.name)).toEqual(['Groceries', 'Transpo', 'Other'])
    expect((await allMethods()).map((m) => m.name)).toEqual(['Cash', 'GCash', 'Old card'])

    const id = await addExpense(100, 10, 1)
    expect(id).toBeGreaterThan(42)
  })

  it('exports what it restored', async () => {
    await restoreJson(v2)
    const exported = JSON.parse(await exportJson())
    expect(exported.expenses).toEqual(JSON.parse(v2).expenses)
    expect(exported.paymentMethods).toEqual(JSON.parse(v2).paymentMethods)
    expect(exported.schemaVersion).toBe(2)
  })

  it('leaves data alone when the file is bad', async () => {
    const before = await allLabels()
    await expect(restoreJson('nope')).rejects.toThrow(DAMAGED)
    expect(await allLabels()).toEqual(before)
  })
})
