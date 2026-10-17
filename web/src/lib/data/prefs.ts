import { writable, type Readable } from 'svelte/store'
import { getDb } from './db'
import type { Label, PaymentMethod } from './models'

export interface Prefs {
  theme_id: string
  voice_language: string
  guide_seen_version: number
  last_label_id: number
  last_payment_method_id: number
  backup_repo: string
  backup_token: string
  backup_enabled: boolean
  last_backup_at: number
  last_backup_error: string
  last_file_backup_at: number
  backup_reminder_dismissed_at: number
  install_banner_dismissed_at: number
  last_seen_version: string
  last_change_at: number
  swipe_hint_seen: boolean
}

const snapshot = writable<Partial<Prefs>>({})

/** Reactive copy of every pref. Call [loadPrefs] once at startup to fill it. */
export const prefsStore: Readable<Partial<Prefs>> = { subscribe: snapshot.subscribe }

export async function loadPrefs(): Promise<Partial<Prefs>> {
  const db = getDb()
  const keys = (await db.getAllKeys('prefs')) as string[]
  const all: Record<string, unknown> = {}
  for (const k of keys) all[k] = await db.get('prefs', k)
  snapshot.set(all as Partial<Prefs>)
  return all as Partial<Prefs>
}

export async function getPref<K extends keyof Prefs>(k: K): Promise<Prefs[K] | undefined> {
  return (await getDb().get('prefs', k)) as Prefs[K] | undefined
}

export async function setPref<K extends keyof Prefs>(k: K, v: Prefs[K]): Promise<void> {
  await getDb().put('prefs', v, k)
  snapshot.update((s) => ({ ...s, [k]: v }))
}

export async function removePref(k: keyof Prefs): Promise<void> {
  await getDb().delete('prefs', k)
  snapshot.update((s) => {
    const next = { ...s }
    delete next[k]
    return next
  })
}

/** Last-used label if it still exists, else the first by position. */
export function resolveLabel(labels: Label[], lastId: number | undefined): Label {
  return labels.find((l) => l.id === lastId) ?? labels[0]
}

/** Last-used method if still active, else the first active one. `active` must not be empty. */
export function resolveMethod(active: PaymentMethod[], lastId: number | undefined): PaymentMethod {
  return active.find((m) => m.id === lastId) ?? active[0]
}
