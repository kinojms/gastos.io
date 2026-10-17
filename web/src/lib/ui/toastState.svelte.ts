export const toast = $state({ text: '', id: 0 })

let timer: ReturnType<typeof setTimeout> | undefined

/** Shows one short message at a time for about 2.5 seconds. */
export function showToast(text: string): void {
  toast.text = text
  toast.id += 1
  clearTimeout(timer)
  timer = setTimeout(() => {
    toast.text = ''
  }, 2500)
}
