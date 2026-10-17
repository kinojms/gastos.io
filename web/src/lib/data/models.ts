import { cssToArgb } from '../domain/color'

export interface Label {
  id: number
  name: string
  colorArgb: number
  position: number
  isOther: boolean
}

export interface PaymentMethod {
  id: number
  name: string
  colorArgb: number
  position: number
  archived: boolean
}

export interface Expense {
  id: number
  amountCentavos: number
  labelId: number
  timestamp: number
  paymentMethodId: number
  description: string | null
  notes: string | null
}

export type ExpenseRow = Expense & {
  labelName: string
  labelColor: number
  paymentMethodName: string
  paymentMethodColor: number
}

export interface DaySummary {
  date: string
  count: number
  totalCentavos: number
}

export interface Total {
  id: number
  name: string
  colorArgb: number
  totalCentavos: number
}

/** Seeded on first run, in this order. Other is the catch-all and cannot be deleted. */
export const DEFAULT_LABELS: Omit<Label, 'id'>[] = [
  { name: 'Food', colorArgb: cssToArgb('#D9822B'), position: 0, isOther: false },
  { name: 'Transpo', colorArgb: cssToArgb('#2F7FC1'), position: 1, isOther: false },
  { name: 'Hobbies', colorArgb: cssToArgb('#8A5CC9'), position: 2, isOther: false },
  { name: 'Bills', colorArgb: cssToArgb('#3A9A6A'), position: 3, isOther: false },
  { name: 'Other', colorArgb: cssToArgb('#77808B'), position: 4, isOther: true },
]

/** Seeded on first run, in this order. Cash comes first. */
export const DEFAULT_PAYMENT_METHODS: { name: string; colorArgb: number }[] = [
  { name: 'Cash', colorArgb: cssToArgb('#77808B') },
  { name: 'GCash', colorArgb: cssToArgb('#007DFE') },
  { name: 'Maribank', colorArgb: cssToArgb('#FF6A13') },
  { name: 'BPI', colorArgb: cssToArgb('#A6192E') },
  { name: 'Maya', colorArgb: cssToArgb('#00B26B') },
  { name: 'BDO', colorArgb: cssToArgb('#0033A0') },
]

export const LABEL_PALETTE: number[] = [
  '#D9822B', '#2F7FC1', '#8A5CC9', '#3A9A6A', '#77808B', '#C2412D', '#C9A227', '#2A9D9D',
].map(cssToArgb)
