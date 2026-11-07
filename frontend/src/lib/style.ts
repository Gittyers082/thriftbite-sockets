import type { CSSProperties } from 'react'

/** Sets the spacing for a .stack / .row / .wrap-row through its --gap variable. */
export function gap(size: number, extra: CSSProperties = {}): CSSProperties {
  return { ['--gap' as string]: `${size}px`, ...extra }
}
