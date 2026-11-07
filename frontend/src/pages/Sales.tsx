import { useCallback, useState } from 'react'
import { api } from '../api/client'
import type { SalesStats, Weekday } from '../api/types'
import { Empty, Loading, ProblemPanel } from '../components/States'
import { clockOf, manilaDate, percent, peso, pesoShort, plural } from '../lib/format'
import { href, paths } from '../lib/route'
import { gap } from '../lib/style'
import { useLoad, useNow } from '../lib/useLoad'

const ranges = [7, 30] as const
type Range = (typeof ranges)[number]

const shortDay: Record<Weekday, string> = {
  MONDAY: 'Mon', TUESDAY: 'Tue', WEDNESDAY: 'Wed', THURSDAY: 'Thu', FRIDAY: 'Fri', SATURDAY: 'Sat', SUNDAY: 'Sun',
}
const longDay = (d: Weekday) => d.charAt(0) + d.slice(1).toLowerCase()

/** "2026-11-07" plus n days, on the calendar (no time zone involved). */
function addDays(date: string, n: number): string {
  const d = new Date(date + 'T00:00:00Z')
  d.setUTCDate(d.getUTCDate() + n)
  return d.toISOString().slice(0, 10)
}

const dateLabel = (date: string) =>
  new Date(date + 'T00:00:00Z').toLocaleDateString('en-PH', { timeZone: 'UTC', month: 'short', day: 'numeric' })

/** The last 7 or 30 full days, ending yesterday in Manila. */
export function Sales() {
  const now = useNow(60 * 60_000)
  const today = manilaDate(now)
  const [range, setRange] = useState<Range>(7)
  const to = addDays(today, -1)
  const from = addDays(today, -range)
  const loaded = useLoad(useCallback(() => api.salesStats(from, to), [from, to]))

  return (
    <div className="stack" style={gap(24)}>
      <div className="page-head">
        <div className="stack" style={gap(6)}>
          <h1 className="wide">Sales</h1>
          <p className="muted">{dateLabel(from)} to {dateLabel(to)}, Manila time</p>
        </div>
        <div role="tablist" aria-label="Period" className="segmented">
          {ranges.map((r) => (
            <button key={r} type="button" role="tab" className="chip" aria-selected={range === r} onClick={() => setRange(r)}>
              Last {r} days
            </button>
          ))}
        </div>
      </div>
      {loaded.error && !loaded.data ? <ProblemPanel error={loaded.error} onRetry={loaded.reload} />
        : !loaded.data ? <Loading label="Adding up your sales…" />
        : <Report stats={loaded.data} range={range} />}
    </div>
  )
}

function Report({ stats, range }: { stats: SalesStats; range: Range }) {
  const t = stats.totals
  if (t.unitsListed === 0 && t.unitsSold === 0) {
    return (
      <Empty title="No sales in these days yet" action={<a className="btn teal" href={href(paths.newListing)}>Post a listing</a>}>
        Once you post food and buyers pick it up, you’ll see what sold, what was left over and when people come.
      </Empty>
    )
  }

  const change = t.moneyMadeChange
  const busiest = [...stats.leftoverByWeekday].filter((w) => w.averagePerDay != null)
    .sort((a, b) => (b.averagePerDay ?? 0) - (a.averagePerDay ?? 0))[0]

  return (
    <>
      <div className="kpis">
        <div className="card kpi" style={{ background: 'var(--lime)' }}>
          <span className="small" style={{ fontWeight: 600 }}>Money made</span>
          <strong className="wide value num">{pesoShort(t.moneyMade)}</strong>
          <span className="note">
            {change == null ? `First ${range} days with sales` : `${change >= 0 ? 'Up' : 'Down'} ${percent(Math.abs(change))} on the ${range} days before`}
          </span>
        </div>
        <div className="card kpi">
          <span className="small muted" style={{ fontWeight: 600 }}>Food saved</span>
          <strong className="wide value num">{t.unitsSold}</strong>
          <span className="note">{plural(t.pickups, 'pickup')}, {pesoShort(t.discountGiven)} off for buyers</span>
        </div>
        <div className="card kpi">
          <span className="small muted" style={{ fontWeight: 600 }}>Sold of what you listed</span>
          <strong className="wide value num">{percent(t.sellThrough)}</strong>
          <span className="note">{t.unitsLeftOver} left over of {t.unitsListed}</span>
        </div>
        <div className="card kpi" style={{ background: (t.missedRate ?? 0) > 0.15 ? 'var(--orange)' : undefined }}>
          <span className="small muted" style={{ fontWeight: 600, color: 'inherit' }}>Missed pickups</span>
          <strong className="wide value num">{t.missedPickups}</strong>
          <span className="note">{percent(t.missedRate)} of reservations, {pesoShort(t.missedValue)} held back</span>
        </div>
      </div>

      <section className="card stack" style={gap(14, { padding: 20 })}>
        <h2 className="wide" style={{ fontSize: 22 }}>Money made each day</h2>
        <Bars
          label="Money made each day"
          bars={stats.days.map((d, i) => ({
            key: d.date,
            value: d.moneyMade,
            axis: range === 7 ? shortDay[d.weekday] : i % 5 === 0 ? dateLabel(d.date) : '',
            title: `${dateLabel(d.date)}: ${peso(d.moneyMade)}, ${plural(d.unitsSold, 'item')}`,
          }))}
          format={pesoShort}
          color="var(--teal)"
          gapPx={range === 7 ? 14 : 4}
        />
      </section>

      <div className="two" style={{ alignItems: 'start' }}>
        <section className="card stack" style={gap(14, { padding: 20 })}>
          <h2 className="wide" style={{ fontSize: 22 }}>When buyers pick up</h2>
          {stats.pickupsByHour.length === 0 ? <p className="muted">No pickups yet.</p> : (
            <Bars
              label="Pickups by hour"
              bars={stats.pickupsByHour.map((h) => ({
                key: String(h.hour),
                value: h.pickups,
                axis: clockOf(`${h.hour}:00`).replace(':00', ''),
                title: `${clockOf(`${h.hour}:00`)}: ${plural(h.pickups, 'pickup')}`,
              }))}
              format={String}
              color="var(--orange)"
              gapPx={8}
            />
          )}
        </section>
        <section className="card stack" style={gap(12, { padding: 20 })}>
          <h2 className="wide" style={{ fontSize: 22 }}>Left over by weekday</h2>
          {busiest && (busiest.averagePerDay ?? 0) > 0 && (
            <p className="small">{longDay(busiest.weekday)}s have the most left over, about {Math.round(busiest.averagePerDay ?? 0)} a day. Try listing less, or a bigger discount.</p>
          )}
          <WeekdayMeters stats={stats} />
        </section>
      </div>

      <section className="card tbl-scroll">
        <table className="tbl">
          <caption className="sr">Sales by item</caption>
          <thead>
            <tr>
              <th scope="col">Food</th><th scope="col" className="r">Listed</th><th scope="col" className="r">Sold</th>
              <th scope="col" className="r">Left over</th><th scope="col">Sold of listed</th>
              <th scope="col" className="r">Money made</th><th scope="col">Usually sells out</th>
            </tr>
          </thead>
          <tbody>
            {stats.items.map((item) => (
              <tr key={item.name}>
                <td><strong>{item.name}</strong><div className="hint">{plural(item.daysListed, 'day')} listed</div></td>
                <td className="r num">{item.listed}</td>
                <td className="r num">{item.sold}</td>
                <td className="r num">{item.leftOver}</td>
                <td style={{ minWidth: 140 }}>
                  <div className="row" style={gap(8)}>
                    <div className="meter grow" aria-hidden="true"><div style={{ width: percent(item.sellThrough).replace('–', '0%'), background: 'var(--teal)' }} /></div>
                    <span className="num small">{percent(item.sellThrough)}</span>
                  </div>
                </td>
                <td className="r num">{peso(item.moneyMade)}</td>
                <td className="small">
                  {item.usualSellOutTime ? `By ${clockOf(item.usualSellOutTime)}` : 'Rarely sells out'}
                  {item.daysSoldOut > 0 && <div className="hint">Sold out {plural(item.daysSoldOut, 'day')}</div>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </>
  )
}

interface Bar { key: string; value: number; axis: string; title: string }

/** A plain bar chart. The numbers are also in a hidden table for screen readers. */
function Bars({ label, bars, format, color, gapPx }: { label: string; bars: Bar[]; format: (n: number) => string; color: string; gapPx: number }) {
  const max = Math.max(1, ...bars.map((b) => b.value))
  const vars = { ['--bar-gap' as string]: `${gapPx}px` }
  return (
    <div className="stack" style={gap(8)}>
      <div className="spread hint"><span>{format(max)}</span></div>
      <div className="chart" style={{ height: 180, ...vars }} aria-hidden="true">
        <div className="grid" style={{ top: 0 }} />
        <div className="grid" style={{ top: '50%' }} />
        {bars.map((b) => (
          <div key={b.key} className="slot" title={b.title}>
            <div className="col" style={{ height: `${(b.value / max) * 100}%`, minHeight: b.value > 0 ? 3 : 0, background: color }} />
          </div>
        ))}
      </div>
      <div className="axis" style={vars} aria-hidden="true">
        {bars.map((b) => <span key={b.key}>{b.axis}</span>)}
      </div>
      <table className="sr">
        <caption>{label}</caption>
        <tbody>{bars.map((b) => <tr key={b.key}><td>{b.title}</td></tr>)}</tbody>
      </table>
    </div>
  )
}

function WeekdayMeters({ stats }: { stats: SalesStats }) {
  const max = Math.max(1, ...stats.leftoverByWeekday.map((w) => w.averagePerDay ?? 0))
  return (
    <div className="stack" style={gap(10)}>
      {stats.leftoverByWeekday.map((w) => (
        <div key={w.weekday} className="row" style={gap(10)}>
          <span style={{ width: 36, fontWeight: 600 }}>{shortDay[w.weekday]}</span>
          <div className="meter grow" aria-hidden="true">
            <div style={{ width: `${((w.averagePerDay ?? 0) / max) * 100}%`, background: 'var(--orange)' }} />
          </div>
          <span className="num small" style={{ width: 76, textAlign: 'right' }}>
            {w.averagePerDay == null ? 'No listings' : `${Math.round(w.averagePerDay * 10) / 10} a day`}
          </span>
        </div>
      ))}
    </div>
  )
}
