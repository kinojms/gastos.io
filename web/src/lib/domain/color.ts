/** Signed 32-bit ARGB int (Kotlin Int, as written in backups) to "#rrggbb"; alpha is ignored. */
export function argbToCss(argb: number): string {
  return '#' + (argb & 0xffffff).toString(16).padStart(6, '0')
}

/** "#rrggbb" to a signed 32-bit ARGB int with alpha FF. */
export function cssToArgb(hex: string): number {
  return (0xff000000 | parseInt(hex.slice(1, 7), 16)) | 0
}

function rgb(hex: string): [number, number, number] {
  return [parseInt(hex.slice(1, 3), 16), parseInt(hex.slice(3, 5), 16), parseInt(hex.slice(5, 7), 16)]
}

/** fg laid over bg at the given opacity (0..1): the Home tint. */
export function blend(fgCss: string, bgCss: string, amount: number): string {
  const f = rgb(fgCss)
  const b = rgb(bgCss)
  return '#' + f.map((v, i) => Math.round(b[i] + (v - b[i]) * amount).toString(16).padStart(2, '0')).join('')
}
