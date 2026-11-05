/** "₱1,234.50": two decimals and thousands separators, no space after the sign. */
export function peso(amount: number): string {
  return '₱' + amount.toLocaleString('en-PH', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** Percent off the usual price, rounded: 25 of 50 -> 50. */
export function percentOff(price: number, usual: number): number {
  if (usual <= 0) return 0
  return Math.max(0, Math.round((1 - price / usual) * 100))
}

/** Minutes until a time given in hours of the day (16.5 = 4:30 PM): "1 h 20 m". */
export function untilLabel(fromHour: number, toHour: number): string {
  const minutes = Math.max(0, Math.round((toHour - fromHour) * 60))
  return `${Math.floor(minutes / 60)} h ${minutes % 60} m`
}

/** 16.5 -> "4:30 PM" */
export function clock(hour: number): string {
  const h = Math.floor(hour)
  const m = Math.round((hour - h) * 60)
  const suffix = h >= 12 ? 'PM' : 'AM'
  const h12 = h % 12 === 0 ? 12 : h % 12
  return m === 0 ? `${h12}:00 ${suffix}` : `${h12}:${String(m).padStart(2, '0')} ${suffix}`
}

export function plural(count: number, one: string, many: string = one + 's'): string {
  return `${count} ${count === 1 ? one : many}`
}
