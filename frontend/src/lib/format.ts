/** All times are shown in Baguio's time zone, whatever the device is set to. */
export const ZONE = 'Asia/Manila'

const timeFmt = new Intl.DateTimeFormat('en-PH', { timeZone: ZONE, hour: 'numeric', minute: '2-digit' })
const dayFmt = new Intl.DateTimeFormat('en-PH', { timeZone: ZONE, month: 'short', day: 'numeric' })
const isoDayFmt = new Intl.DateTimeFormat('en-CA', { timeZone: ZONE, year: 'numeric', month: '2-digit', day: '2-digit' })

/** "₱1,234.50": two decimals and thousands separators, no space after the sign. */
export function peso(amount: number): string {
  return '₱' + amount.toLocaleString('en-PH', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** "₱1,235", for big round numbers in charts and tiles. */
export function pesoShort(amount: number): string {
  return '₱' + Math.round(amount).toLocaleString('en-PH')
}

export function percent(ratio: number | null | undefined): string {
  return ratio == null ? '–' : `${Math.round(ratio * 100)}%`
}

export function plural(count: number, one: string, many: string = one + 's'): string {
  return `${count} ${count === 1 ? one : many}`
}

/** "5:00 PM" in Manila time. */
export function timeOf(iso: string): string {
  return timeFmt.format(new Date(iso))
}

/** "Nov 7" in Manila time. */
export function dayOf(iso: string): string {
  return dayFmt.format(new Date(iso))
}

/** "5:00 to 7:00 PM" */
export function windowOf(startIso: string, endIso: string): string {
  const start = timeOf(startIso)
  const end = timeOf(endIso)
  const startSuffix = start.slice(-2)
  return end.endsWith(startSuffix) ? `${start.slice(0, -3)} to ${end}` : `${start} to ${end}`
}

/** "1 h 20 m" from now (or "0 m" once it's passed). */
export function untilLabel(iso: string, now: number): string {
  const minutes = Math.max(0, Math.round((new Date(iso).getTime() - now) / 60_000))
  const h = Math.floor(minutes / 60)
  return h > 0 ? `${h} h ${minutes % 60} m` : `${minutes} m`
}

/** Today's date in Manila, as "2026-11-07". */
export function manilaDate(at: number): string {
  return isoDayFmt.format(new Date(at))
}

/** A Manila date and "17:30" as an ISO instant. The Philippines has no daylight saving, so +08:00 always holds. */
export function manilaInstant(date: string, time: string): string {
  return new Date(`${date}T${time}:00+08:00`).toISOString()
}

/** "16:39:00" -> "4:39 PM" */
export function clockOf(localTime: string): string {
  const [h, m] = localTime.split(':').map(Number)
  const suffix = h >= 12 ? 'PM' : 'AM'
  const h12 = h % 12 === 0 ? 12 : h % 12
  return `${h12}:${String(m).padStart(2, '0')} ${suffix}`
}

/** "482913" -> "482 913", easier to read out at the counter. */
export function spacedCode(code: string): string {
  return code.length === 6 ? `${code.slice(0, 3)} ${code.slice(3)}` : code
}
