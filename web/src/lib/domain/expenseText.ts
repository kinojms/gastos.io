export const DESCRIPTION_MAX = 60
export const NOTES_MAX = 1000

function clean(raw: string | null | undefined, max: number): string | null {
  const trimmed = raw?.trim()
  if (!trimmed) return null
  return trimmed.slice(0, max).trimEnd()
}

/** Trimmed, blank becomes null, cut to 60 characters. */
export function cleanDescription(raw: string | null | undefined): string | null {
  return clean(raw, DESCRIPTION_MAX)
}

/** Trimmed, blank becomes null, cut to 1000 characters. */
export function cleanNotes(raw: string | null | undefined): string | null {
  return clean(raw, NOTES_MAX)
}
