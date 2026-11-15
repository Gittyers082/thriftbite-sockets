import { useCallback, useState } from 'react'
import { api, photoUrl } from '../api/client'
import type { StoreProfile, UsualListing } from '../api/types'
import { Empty, Loading, ProblemPanel } from '../components/States'
import { messageOf } from '../lib/errors'
import { addDays, clockOf, clockWindow, dateLabel, manilaDate, manilaInstant, peso, plural } from '../lib/format'
import { href, paths } from '../lib/route'
import { gap } from '../lib/style'
import { useLoad, useNow } from '../lib/useLoad'

/** "sold 6 of 6, gone by 4:19 PM" */
function lastTime(u: UsualListing): string {
  const sold = `sold ${u.lastSold} of ${u.lastListed}`
  return u.lastSoldOutAt ? `${sold}, gone by ${clockOf(u.lastSoldOutAt)}` : sold
}

/** Re-list what the store usually sells, with last time's amounts, in one go. */
export function PostAgain() {
  const now = useNow()
  const today = manilaDate(now)
  const [tomorrow, setTomorrow] = useState(false)
  const day = tomorrow ? addDays(today, 1) : today
  const loaded = useLoad(useCallback(() => Promise.all([api.usualListings(day), api.myStore()]), [day]))

  return (
    <div className="stack" style={gap(24)}>
      <div className="page-head">
        <div className="stack" style={gap(6)}>
          <a className="small" href={href(paths.owner)} style={{ fontWeight: 600 }}>Back to today</a>
          <h1 className="wide">Post again</h1>
          <p className="muted">Your usual listings, set to last time’s amounts. Change what you need and post them all at once.</p>
        </div>
        <div role="tablist" aria-label="Pickup day" className="segmented">
          <button type="button" role="tab" className="chip" aria-selected={!tomorrow} onClick={() => setTomorrow(false)}>
            Today, {dateLabel(today)}
          </button>
          <button type="button" role="tab" className="chip" aria-selected={tomorrow} onClick={() => setTomorrow(true)}>
            Tomorrow, {dateLabel(addDays(today, 1))}
          </button>
        </div>
      </div>
      {loaded.error && !loaded.data ? <ProblemPanel error={loaded.error} onRetry={loaded.reload} />
        : !loaded.data || loaded.loading ? <Loading label="Looking up your usual listings…" />
        : loaded.data[0].length === 0 ? (
          <Empty title="Nothing to post again yet" action={<a className="btn teal" href={href(paths.newListing)}>Post a listing</a>}>
            Once a listing’s pickup time has passed, it shows up here so you can post it again in two taps.
          </Empty>
        ) : (
          // The key starts the form over when the day (and so the list) changes.
          <Usual key={day} usual={loaded.data[0]} store={loaded.data[1]} day={day} dayWord={tomorrow ? 'tomorrow' : 'today'} now={now} />
        )}
    </div>
  )
}

interface Pick { on: boolean; quantity: number }

function Usual({ usual, store, day, dayWord, now }: { usual: UsualListing[]; store: StoreProfile; day: string; dayWord: string; now: number }) {
  const [picks, setPicks] = useState<Record<string, Pick>>(() =>
    Object.fromEntries(usual.map((u) => [u.name, { on: !u.alreadyListed, quantity: Math.max(1, u.lastListed) }])))
  const [start, setStart] = useState(store.defaultPickupStart?.slice(0, 5) ?? '17:00')
  const [end, setEnd] = useState(store.defaultPickupEnd?.slice(0, 5) ?? '19:00')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [posted, setPosted] = useState<number | null>(null)

  const set = (name: string, patch: Partial<Pick>) => setPicks({ ...picks, [name]: { ...picks[name], ...patch } })
  const chosen = usual.filter((u) => picks[u.name].on && !u.alreadyListed)
  const units = chosen.reduce((sum, u) => sum + picks[u.name].quantity, 0)
  const timeProblem = !start || !end || start >= end ? 'Pickup has to end after it starts.'
    : new Date(manilaInstant(day, end)).getTime() <= now ? 'That pickup time has already passed today. Pick a later time, or post for tomorrow.'
    : null

  const post = async () => {
    setError(null)
    setBusy(true)
    try {
      const made = await api.postBatch({
        pickupStart: manilaInstant(day, start),
        pickupEnd: manilaInstant(day, end),
        items: chosen.map((u) => ({
          name: u.name, description: u.description ?? undefined, price: u.price, originalPrice: u.originalPrice,
          quantity: picks[u.name].quantity, pickupLocation: u.pickupLocation, photoId: u.photoId,
        })),
      })
      setPosted(made.length)
    } catch (err) {
      setError(messageOf(err))
    } finally {
      setBusy(false)
    }
  }

  if (posted != null) {
    return (
      <div role="status" className="card notice good" style={{ flexWrap: 'wrap' }}>
        <div className="grow">
          <strong style={{ fontSize: 18 }}>Posted {plural(posted, 'listing')} for {dayWord}.</strong>
          <p>Buyers can reserve them now. Pickup is {clockWindow(start, end)}.</p>
        </div>
        <a className="btn" href={href(paths.owner)}>Back to today</a>
      </div>
    )
  }

  return (
    <div className="cart-layout">
      <section className="card" aria-label="Your usual listings">
        {usual.map((u) => {
          const pick = picks[u.name]
          const id = `again-${u.name}`
          const active = pick.on && !u.alreadyListed
          return (
            <div key={u.name} className={`usual-row${active ? '' : ' off'}`}>
              <input id={id} type="checkbox" checked={active} disabled={u.alreadyListed}
                onChange={(e) => set(u.name, { on: e.target.checked })} style={{ width: 20, height: 20, accentColor: 'var(--ink)' }} />
              <div className="row" style={gap(12)}>
                {u.photoId && <img className="thumb" src={photoUrl(u.photoId, 'thumb')} alt="" loading="lazy" />}
                <div className="stack grow" style={gap(2)}>
                  <label htmlFor={id} style={{ fontWeight: 700, cursor: u.alreadyListed ? undefined : 'pointer' }}>{u.name}</label>
                  <span className="small num">{peso(u.price)} <s className="muted">{peso(u.originalPrice)}</s></span>
                  <span className="hint">
                    {u.sameWeekday ? `Last ${dateLabel(u.lastDay)}` : `Last time, ${dateLabel(u.lastDay)}`}: {lastTime(u)}
                  </span>
                </div>
              </div>
              {u.alreadyListed ? <span className="tag qty">Already on sale {dayWord}</span> : (
                <div className="qty">
                  {u.suggestedQuantity != null && u.suggestedQuantity !== pick.quantity && (
                    <button type="button" className="chip" onClick={() => set(u.name, { quantity: u.suggestedQuantity ?? pick.quantity, on: true })}>
                      Try {u.suggestedQuantity}
                    </button>
                  )}
                  <button type="button" className="step" aria-label={`One less ${u.name}`} disabled={!active || pick.quantity <= 1}
                    onClick={() => set(u.name, { quantity: pick.quantity - 1 })}>−</button>
                  <span className="num wide" style={{ width: 30, textAlign: 'center', fontSize: 18 }} aria-live="polite">{pick.quantity}</span>
                  <button type="button" className="step" aria-label={`One more ${u.name}`} disabled={!active || pick.quantity >= 999}
                    onClick={() => set(u.name, { quantity: pick.quantity + 1 })}>+</button>
                </div>
              )}
            </div>
          )
        })}
      </section>

      <aside className="card stack" style={gap(14, { padding: 20, alignSelf: 'start' })}>
        <h2 className="wide" style={{ fontSize: 20 }}>For every listing</h2>
        <div className="two" style={{ gap: 12 }}>
          <div className="field">
            <label htmlFor="again-start">Pickup from</label>
            <input id="again-start" className={`input${timeProblem ? ' bad' : ''}`} type="time" value={start} onChange={(e) => setStart(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="again-end">Pickup until</label>
            <input id="again-end" className={`input${timeProblem ? ' bad' : ''}`} type="time" value={end} onChange={(e) => setEnd(e.target.value)} />
          </div>
        </div>
        {timeProblem
          ? <span role="alert" className="err">{timeProblem}</span>
          : <span className="hint">From your <a href={href(paths.storeProfile)}>store profile</a>. Change it there to update the default.</span>}
        <p style={{ borderTop: '2px solid var(--ink)', paddingTop: 12 }}>
          {chosen.length === 0
            ? 'Tick the listings you want to post.'
            : `${plural(units, 'unit')} for pickup ${dayWord}${timeProblem ? '' : `, ${clockWindow(start, end)}`}, at ${store.name}.`}
        </p>
        {error && <p role="alert" className="err">{error}</p>}
        <button type="button" className="btn teal big" disabled={busy || chosen.length === 0 || timeProblem != null} onClick={post}>
          {busy ? 'Posting…' : chosen.length === 0 ? 'Pick at least one listing' : `Post ${plural(chosen.length, 'listing')}`}
        </button>
      </aside>
    </div>
  )
}
