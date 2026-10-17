/** The one expense row whose actions are showing, if any. Opening another closes it. */
export const openRow = $state<{ id: number | null }>({ id: null })

export function closeOpenRow(): void {
  openRow.id = null
}
