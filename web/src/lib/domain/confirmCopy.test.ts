import { describe, expect, it } from 'vitest'
import { confirmCopy as c } from './confirmCopy'

describe('confirmCopy', () => {
  it('expense copy', () => {
    expect(c.saveExpense(4550, 'Transpo')).toBe('Save changes to ₱45.50 · Transpo?')
    expect(c.deleteExpense(4550, 'Transpo')).toBe('Delete ₱45.50 · Transpo?')
  })

  it('label copy', () => {
    expect(c.addLabel('Pets')).toBe('Add label Pets?')
    expect(c.renameLabel('Food', 'Meals')).toBe('Rename Food to Meals?')
    expect(c.recolorLabel('Food')).toBe('Change color of Food?')
    expect(c.reorderLabels()).toBe('Save new label order?')
    expect(c.deleteLabel('Pets', 0)).toBe('Delete label Pets?')
    expect(c.deleteLabel('Pets', 1)).toBe('Delete label Pets? 1 expense moves to Other.')
    expect(c.deleteLabel('Hobbies', 12)).toBe('Delete label Hobbies? 12 expenses move to Other.')
  })

  it('method copy', () => {
    expect(c.addMethod('Wise')).toBe('Add payment method Wise?')
    expect(c.renameMethod('GCash', 'G')).toBe('Rename GCash to G?')
    expect(c.recolorMethod('GCash')).toBe('Change color of GCash?')
    expect(c.reorderMethods()).toBe('Save new payment method order?')
    expect(c.deleteMethod('BDO')).toBe('Delete BDO? Past expenses keep it.')
  })

  it('backup, theme and voice copy', () => {
    expect(c.restore()).toBe('Restore from GitHub? This replaces all data on this phone.')
    expect(c.startFresh()).toBe("Start fresh? Any existing backup in the repo will be replaced with this phone's data.")
    expect(c.saveBackupSettings()).toBe('Save backup settings?')
    expect(c.restoreFile(1, 'Oct 16, 2026')).toBe('Replace all expenses with this backup (1 expense from Oct 16, 2026)?')
    expect(c.restoreFile(12, 'Oct 16, 2026')).toBe('Replace all expenses with this backup (12 expenses from Oct 16, 2026)?')
    expect(c.changeTheme('Nord')).toBe('Change theme to Nord?')
    expect(c.changeVoiceLanguage('Tagalog')).toBe('Change voice language to Tagalog?')
  })
})
