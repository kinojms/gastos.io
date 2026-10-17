export interface ShareableFile {
  name: string
  mime: string
  bytes: Uint8Array
}

export type ShareOutcome = 'shared' | 'downloaded' | 'cancelled'

/**
 * Hands a file to the user: the iOS share sheet (Save to Files, AirDrop, Messages...) when the browser can
 * share files, else a plain download.
 */
export async function shareOrDownload(file: ShareableFile): Promise<ShareOutcome> {
  const blob = new Blob([file.bytes as BlobPart], { type: file.mime })
  const f = new File([blob], file.name, { type: file.mime })
  if (typeof navigator !== 'undefined' && navigator.canShare?.({ files: [f] })) {
    try {
      await navigator.share({ files: [f] })
      return 'shared'
    } catch (e) {
      if (e instanceof DOMException && e.name === 'AbortError') return 'cancelled'
      // Any other share failure falls through to a download.
    }
  }
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = file.name
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
  return 'downloaded'
}
