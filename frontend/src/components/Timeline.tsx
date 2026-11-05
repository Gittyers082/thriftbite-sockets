import { clock } from '../lib/format'

/** Position of an hour on an axis, as a CSS percentage. */
function at(hour: number, start: number, end: number): string {
  return `${(((hour - start) / (end - start)) * 100).toFixed(2)}%`
}

interface AxisProps {
  start: number
  end: number
  step: number
}

/** Hour labels on one step, first pulled in from the left edge, last from the right. */
export function Axis({ start, end, step }: AxisProps) {
  const hours: number[] = []
  for (let h = start; h <= end; h += step) hours.push(h)
  return (
    <div className="axis" aria-hidden="true">
      {hours.map((h, i) => {
        const shift = i === 0 ? '0' : i === hours.length - 1 ? '-100%' : '-50%'
        return (
          <span key={h} style={{ left: at(h, start, end), transform: `translateX(${shift})` }}>
            {clock(h).replace(':00', '')}
          </span>
        )
      })}
    </div>
  )
}

export interface WindowRow {
  label: string
  from: number
  to: number
}

interface PickupWindowsProps {
  rows: WindowRow[]
  now: number
  start: number
  end: number
}

/** Each store's pickup window on a shared evening axis, with a line at "now". */
export function PickupWindows({ rows, now, start, end }: PickupWindowsProps) {
  return (
    <div className="timeline">
      <span />
      <Axis start={start} end={end} step={1} />
      {rows.map((row) => (
        <div key={row.label} style={{ display: 'contents' }}>
          <span className="label">{row.label}</span>
          <div className="track" role="img" aria-label={`${row.label}: pickup ${clock(row.from)} to ${clock(row.to)}`}>
            <div className="bar" style={{ left: at(row.from, start, end), width: at(row.to - row.from + start, start, end) }} />
            <div className="now" style={{ left: at(now, start, end) }} />
          </div>
        </div>
      ))}
      <span />
      <div style={{ position: 'relative', height: 16, fontSize: 12, fontWeight: 600 }}>
        <span style={{ position: 'absolute', left: at(now, start, end), transform: 'translateX(-50%)', whiteSpace: 'nowrap' }}>
          Now {clock(now)}
        </span>
      </div>
    </div>
  )
}

interface SellOutProps {
  name: string
  today: number
  compare: number
  opens: number
  closes: number
}

/** One line that sold out: a teal dot for today, a tick for last Sunday, on the store's day. */
export function SellOutRow({ name, today, compare, opens, closes }: SellOutProps) {
  return (
    <div className="stack" style={{ gap: 6 }}>
      <span style={{ fontWeight: 600 }}>{name}</span>
      <div style={{ position: 'relative', height: 14 }} role="img"
        aria-label={`Sold out at ${clock(today)} today, ${clock(compare)} last Sunday`}>
        <div style={{ position: 'absolute', left: 0, right: 0, top: 6, height: 2, background: 'var(--border)' }} />
        <div style={{ position: 'absolute', left: at(compare, opens, closes), top: 1, width: 2, height: 12, background: 'var(--muted)', transform: 'translateX(-1px)' }} />
        <div style={{ position: 'absolute', left: at(today, opens, closes), top: 2, width: 10, height: 10, borderRadius: 999, background: 'var(--teal)', transform: 'translateX(-5px)' }} />
      </div>
      <Axis start={opens} end={closes} step={2} />
    </div>
  )
}
