import { formatMoney } from './money'

/** Exact confirm-dialog copy shared across screens; mirrors ui/ConfirmCopy.kt. */
export const confirmCopy = {
  saveExpense: (amount: number, label: string) => `Save changes to ${formatMoney(amount)} · ${label}?`,
  deleteExpense: (amount: number, label: string) => `Delete ${formatMoney(amount)} · ${label}?`,
  addLabel: (name: string) => `Add label ${name}?`,
  renameLabel: (old: string, next: string) => `Rename ${old} to ${next}?`,
  recolorLabel: (name: string) => `Change color of ${name}?`,
  reorderLabels: () => 'Save new label order?',
  deleteLabel: (name: string, count: number) => {
    if (count === 0) return `Delete label ${name}?`
    if (count === 1) return `Delete label ${name}? 1 expense moves to Other.`
    return `Delete label ${name}? ${count} expenses move to Other.`
  },
  addMethod: (name: string) => `Add payment method ${name}?`,
  renameMethod: (old: string, next: string) => `Rename ${old} to ${next}?`,
  recolorMethod: (name: string) => `Change color of ${name}?`,
  reorderMethods: () => 'Save new payment method order?',
  deleteMethod: (name: string) => `Delete ${name}? Past expenses keep it.`,
  restore: () => 'Restore from GitHub? This replaces all data on this phone.',
  restoreFile: (count: number, date: string) =>
    `Replace all expenses with this backup (${count === 1 ? '1 expense' : `${count} expenses`} from ${date})?`,
  startFresh: () => "Start fresh? Any existing backup in the repo will be replaced with this phone's data.",
  saveBackupSettings: () => 'Save backup settings?',
  changeTheme: (name: string) => `Change theme to ${name}?`,
  sendFeedback: () => 'Send this feedback?',
  changeVoiceLanguage: (name: string) => `Change voice language to ${name}?`,
}
