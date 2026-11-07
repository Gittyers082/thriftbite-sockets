import { useCallback, useState, type FormEvent, type ReactNode } from 'react'
import { ApiError, api } from '../api/client'
import type { ItemRequest, ItemResponse, ItemStatus, ReservationResponse } from '../api/types'
import { Empty, Loading, ProblemPanel } from '../components/States'
import { messageOf } from '../lib/errors'
import { dayOf, peso, plural, timeOf, untilLabel, windowOf } from '../lib/format'
import { href, paths } from '../lib/route'
import { gap } from '../lib/style'
import { useLoad, useNow } from '../lib/useLoad'

const statusTag: Record<ItemStatus, [string, string]> = {
  AVAILABLE: ['On sale', 'lime'],
  SOLD_OUT: ['Sold out', 'orange'],
  EXPIRED: ['Ended', ''],
  REMOVED: ['Removed', ''],
}

/** What the API needs to save a listing, from a listing it sent. */
function requestFrom(item: ItemResponse, changes: Partial<ItemRequest> = {}): ItemRequest {
  return {
    name: item.name,
    description: item.description ?? undefined,
    price: item.price,
    originalPrice: item.originalPrice,
    quantity: item.quantity,
    expiresAt: item.expiresAt,
    pickupLocation: item.pickupLocation,
    pickupStart: item.pickupStart,
    pickupEnd: item.pickupEnd,
    version: item.version,
    ...changes,
  }
}

/** The store's counter screen: who's coming to pick up, and what's still on sale. */
export function OwnerToday() {
  const now = useNow()
  const loaded = useLoad(useCallback(() => Promise.all([api.ownerPickups(), api.myItems()]), []))
  const [handedOver, setHandedOver] = useState<string | null>(null)
  const [problem, setProblem] = useState<string | null>(null)
  const [removing, setRemoving] = useState<string | null>(null)
  const [saving, setSaving] = useState<string | null>(null)

  if (loaded.error && !loaded.data) return <ProblemPanel error={loaded.error} onRetry={loaded.reload} />
  if (!loaded.data) return <Loading label="Loading today’s pickups…" />

  const [pickups, items] = loaded.data
  const live = items.filter((i) => i.status === 'AVAILABLE' || i.status === 'SOLD_OUT')
  const ended = items.filter((i) => i.status === 'EXPIRED')
  const storeName = items[0]?.storeName ?? pickups[0]?.storeName ?? 'Your store'
  const toCollect = pickups.reduce((sum, r) => sum + r.total, 0)
  const leftOnSale = live.reduce((sum, i) => sum + (i.status === 'AVAILABLE' ? i.quantity : 0), 0)

  const change = async (item: ItemResponse, changes: Partial<ItemRequest>) => {
    setProblem(null)
    setSaving(item.id)
    try {
      await api.updateItem(item.id, requestFrom(item, changes))
    } catch (error) {
      setProblem(error instanceof ApiError && error.status === 409
        ? `${item.name} changed while you were looking (someone may have just reserved it). Here’s the latest.`
        : messageOf(error))
    } finally {
      setSaving(null)
      loaded.reload()
    }
  }

  const remove = async (item: ItemResponse) => {
    setProblem(null)
    try {
      await api.removeItem(item.id)
      setRemoving(null)
      loaded.reload()
    } catch (error) {
      setProblem(messageOf(error))
    }
  }

  return (
    <div className="stack" style={gap(28)}>
      <div className="page-head">
        <div className="stack" style={gap(6)}>
          <h1 className="wide">{storeName}</h1>
          <p className="muted">Today, {dayOf(new Date(now).toISOString())}</p>
        </div>
        <a className="btn teal" href={href(paths.newListing)}>Post a listing</a>
      </div>

      <div className="kpis">
        <Kpi label="Waiting for pickup" value={String(pickups.length)} note={plural(pickups.reduce((n, r) => n + r.items.reduce((m, l) => m + l.quantity, 0), 0), 'item')} />
        <Kpi label="To collect" value={peso(toCollect)} note="Paid at your counter" />
        <Kpi label="Still on sale" value={String(leftOnSale)} note={plural(live.filter((i) => i.status === 'AVAILABLE').length, 'listing')} />
        <Kpi label="Sales and trends" value="See all" note={<a href={href(paths.sales)}>Open sales</a>} />
      </div>

      {handedOver && (
        <div role="status" className="panel notice good">
          <strong className="grow">{handedOver}</strong>
          <button type="button" className="btn sm" onClick={() => setHandedOver(null)}>OK</button>
        </div>
      )}
      {problem && <p role="alert" className="err">{problem}</p>}

      <section className="stack" style={gap(14)}>
        <h2 className="wide" style={{ fontSize: 24 }}>Pickups</h2>
        {pickups.length === 0 ? (
          <p className="panel cream" style={{ padding: '18px 20px' }}>
            No one is waiting right now. New reservations show up here, each with a code the buyer reads out at your counter.
          </p>
        ) : (
          <div className="two" style={{ alignItems: 'start' }}>
            {pickups.map((r) => (
              <PickupCard key={r.id} reservation={r} now={now}
                onDone={(message) => { setHandedOver(message); loaded.reload() }} />
            ))}
          </div>
        )}
      </section>

      <section className="stack" style={gap(14)}>
        <div className="spread">
          <h2 className="wide" style={{ fontSize: 24 }}>Listings</h2>
          {live.length > 0 && <a className="btn sm" href={href(paths.newListing)}>Post another</a>}
        </div>
        {live.length === 0 ? (
          <Empty title="Nothing on sale yet" action={<a className="btn teal" href={href(paths.newListing)}>Post a listing</a>}>
            Post what’s left at the end of the day and buyers nearby can reserve it.
          </Empty>
        ) : (
          <div className="card tbl-scroll">
            <table className="tbl">
              <thead>
                <tr>
                  <th scope="col">Food</th><th scope="col" className="r">Price</th><th scope="col">Left</th>
                  <th scope="col">Pickup</th><th scope="col">Status</th><th scope="col"><span className="sr">Actions</span></th>
                </tr>
              </thead>
              <tbody>
                {live.map((item) => (
                  <tr key={item.id}>
                    <td><strong>{item.name}</strong><div className="hint">Closes in {untilLabel(item.pickupEnd, now)}</div></td>
                    <td className="r num">{peso(item.price)}<div className="hint"><s>{peso(item.originalPrice)}</s></div></td>
                    <td>
                      <div className="row" style={gap(6)}>
                        <button type="button" className="step" aria-label={`One less ${item.name}`}
                          disabled={item.quantity === 0 || saving === item.id} onClick={() => change(item, { quantity: item.quantity - 1 })}>−</button>
                        <span className="num" style={{ minWidth: 28, textAlign: 'center', fontWeight: 800 }} aria-live="polite">{item.quantity}</span>
                        <button type="button" className="step" aria-label={`One more ${item.name}`}
                          disabled={saving === item.id} onClick={() => change(item, { quantity: item.quantity + 1 })}>+</button>
                      </div>
                    </td>
                    <td className="small">{windowOf(item.pickupStart, item.pickupEnd)}</td>
                    <td><span className={`tag ${statusTag[item.status][1]}`}>{statusTag[item.status][0]}</span></td>
                    <td className="r">
                      {removing === item.id ? (
                        <span className="row" style={gap(6, { justifyContent: 'flex-end' })}>
                          <button type="button" className="btn sm" onClick={() => setRemoving(null)}>Keep</button>
                          <button type="button" className="btn sm danger" onClick={() => remove(item)}>Remove</button>
                        </span>
                      ) : (
                        <button type="button" className="link-btn" onClick={() => setRemoving(item.id)}>Take down</button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <p className="hint">Taking a listing down keeps reservations already made. Those buyers can still pick up.</p>
        {ended.length > 0 && <p className="muted small">{plural(ended.length, 'listing')} ended earlier and {ended.length === 1 ? 'is' : 'are'} counted in Sales.</p>}
      </section>
    </div>
  )
}

function Kpi({ label, value, note }: { label: string; value: string; note: ReactNode }) {
  return (
    <div className="card kpi">
      <span className="small muted" style={{ fontWeight: 600 }}>{label}</span>
      <strong className="wide value num">{value}</strong>
      <span className="note">{note}</span>
    </div>
  )
}

/** One buyer on their way: type the code they read out to hand the food over. */
function PickupCard({ reservation: r, now, onDone }: { reservation: ReservationResponse; now: number; onDone: (message: string) => void }) {
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const late = new Date(r.pickupDeadline).getTime() - now < 15 * 60_000

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await api.completePickup(r.id, code.replace(/\D/g, ''))
      onDone(`Handed over to ${r.buyerName}. Collect ${peso(r.total)}.`)
    } catch (err) {
      if (err instanceof ApiError && err.code === 'claim_code_locked') {
        setError(`Too many wrong codes. Try again in about ${Math.ceil((err.retryAfter ?? 300) / 60)} min, or ask the buyer to show the code on their phone.`)
      } else {
        setError(messageOf(err))
      }
      setBusy(false)
    }
  }

  return (
    <section className="card">
      <div className="card-head" style={{ background: late ? 'var(--orange)' : 'var(--glow)' }}>
        <div>
          <h3 style={{ fontSize: 18, fontWeight: 800 }}>{r.buyerName}</h3>
          <p className="small">Reserved {timeOf(r.createdAt)}</p>
        </div>
        <span className="tag">Due {timeOf(r.pickupDeadline)}</span>
      </div>
      <div className="stack" style={gap(8, { padding: '16px 20px' })}>
        {r.items.map((line) => (
          <p key={line.itemId} className="spread num"><span>{line.quantity} × {line.name}</span><span>{peso(line.unitPrice * line.quantity)}</span></p>
        ))}
        <p className="spread num" style={{ borderTop: '2px solid var(--ink)', paddingTop: 8, fontWeight: 800 }}>
          <span>Collect</span><span>{peso(r.total)}</span>
        </p>
        <form className="stack" style={gap(8, { marginTop: 6 })} onSubmit={submit}>
          <label htmlFor={`code-${r.id}`} style={{ fontWeight: 700 }}>Claim code</label>
          <div className="row" style={gap(8)}>
            <input id={`code-${r.id}`} className={`input num grow${error ? ' bad' : ''}`} inputMode="numeric" autoComplete="off"
              placeholder="6 digits" maxLength={7} required value={code}
              onChange={(e) => setCode(e.target.value)} aria-describedby={error ? `code-err-${r.id}` : undefined}
              style={{ fontSize: 20, letterSpacing: 2 }} />
            <button type="submit" className="btn teal" disabled={busy || code.replace(/\D/g, '').length !== 6}>
              {busy ? 'Checking…' : 'Hand over'}
            </button>
          </div>
          {error && <p id={`code-err-${r.id}`} role="alert" className="err">{error}</p>}
        </form>
      </div>
    </section>
  )
}
