import { useCallback, useState } from 'react'
import { ApiError, api, photoUrl } from '../api/client'
import type { FollowState, StoreProfile } from '../api/types'
import { DealCard } from '../components/DealCard'
import { LiveBadge } from '../components/LiveBadge'
import { Loading, ProblemPanel } from '../components/States'
import { messageOf } from '../lib/errors'
import { clockOf, clockWindow, manilaClock, manilaDate, percent, peso, plural, weekdayOf } from '../lib/format'
import { href, navigate, paths } from '../lib/route'
import { gap } from '../lib/style'
import { useCart } from '../lib/useCart'
import { useLiveStock } from '../lib/useLiveStock'
import { useLoad, useNow } from '../lib/useLoad'
import { NotFound } from './Info'

const dayName = (weekday: string) => weekday.charAt(0) + weekday.slice(1).toLowerCase()

/** "Open until 7:00 PM", "Opens at 7:00 AM", "Closed today", or nothing if the store hasn't said. */
function openNow(store: StoreProfile, now: number): { label: string; open: boolean } | null {
  if (store.hours.length === 0) return null
  const today = store.hours.find((h) => h.weekday === weekdayOf(manilaDate(now)))
  if (!today) return { label: 'Closed today', open: false }
  const clock = manilaClock(now)
  if (clock < today.opens.slice(0, 5)) return { label: `Opens at ${clockOf(today.opens)}`, open: false }
  if (clock >= today.closes.slice(0, 5)) return { label: 'Closed for today', open: false }
  return { label: `Open until ${clockOf(today.closes)}`, open: true }
}

/** A store's public page: where it is, how it's done lately, and what it has for pickup. */
export function StorePage({ slug }: { slug: string }) {
  const now = useNow()
  const loaded = useLoad(useCallback(() => api.store(slug), [slug]))
  const { cart, inCart, canReserve, loggedIn, adding, problem, add } = useCart()
  const mutate = loaded.mutate
  const changes = useLiveStock(loaded.data?.items,
    useCallback((change) => mutate((page) => ({ ...page, items: change(page.items) })), [mutate]),
    loaded.reload)

  // Following: what the page loaded with, until the buyer taps the button.
  const [changed, setChanged] = useState<FollowState | null>(null)
  const [followBusy, setFollowBusy] = useState(false)
  const [followProblem, setFollowProblem] = useState<string | null>(null)

  if (loaded.error instanceof ApiError && loaded.error.status === 404) return <NotFound />
  if (loaded.error && !loaded.data) return <ProblemPanel error={loaded.error} onRetry={loaded.reload} />
  if (!loaded.data) return <Loading label="Loading the store…" />

  const { store, items, stats } = loaded.data
  const open = openNow(store, now)
  const place = [store.address, store.barangay].filter(Boolean).join(', ')
  const mapQuery = encodeURIComponent([store.name, store.address, store.barangay, 'Baguio City'].filter(Boolean).join(', '))
  const onSale = items.filter((item) => item.status === 'AVAILABLE' && item.quantity > 0).length
  const mine = cart?.lines.filter((line) => line.storeId === store.id) ?? []
  const follow = changed ?? loaded.data.viewer
  const isBuyer = loggedIn && canReserve

  const toggleFollow = async () => {
    if (!loggedIn) return navigate(paths.login)
    setFollowProblem(null)
    setFollowBusy(true)
    try {
      if (follow?.following) {
        await api.unfollow(slug)
        setChanged({ following: false, alertsOn: false })
      } else {
        setChanged(await api.follow(slug))
      }
      // the follower count on the page moves with it
      const step = follow?.following ? -1 : 1
      mutate((page) => ({ ...page, stats: { ...page.stats, followers: Math.max(0, page.stats.followers + step) } }))
    } catch (error) {
      setFollowProblem(messageOf(error))
    } finally {
      setFollowBusy(false)
    }
  }

  const record = [
    stats.mealsRescued > 0 && { value: stats.mealsRescued.toLocaleString('en-PH'), label: `${stats.mealsRescued === 1 ? 'meal' : 'meals'} rescued in the last 30 days` },
    stats.pickupRate != null && { value: percent(stats.pickupRate), label: 'of reservations were picked up' },
    stats.followers > 0 && { value: stats.followers.toLocaleString('en-PH'), label: stats.followers === 1 ? 'follower' : 'followers' },
  ].filter((entry): entry is { value: string; label: string } => Boolean(entry))

  return (
    <div className="stack" style={gap(28)}>
      <section className={`card cream store-head${store.photoId ? ' with-photo' : ''}`}>
        {store.photoId && (
          <div className="photo shot store-shot"><img src={photoUrl(store.photoId)} alt={`${store.name}, from outside`} /></div>
        )}
        <div className="stack" style={gap(16)}>
        <a href={href(paths.browse)} className="muted small" style={{ fontWeight: 600 }}>Back to browse</a>
        <div className="wrap-row" style={gap(14, { alignItems: 'center' })}>
          <h1 className="wide" style={{ fontSize: 'clamp(32px, 5vw, 56px)', lineHeight: 1.04, letterSpacing: -1.5 }}>{store.name}</h1>
          {open && <span className={`tag ${open.open ? 'lime' : ''} tilt`} style={{ height: 32, fontSize: 15 }}>{open.label}</span>}
        </div>
        {(place || store.landmark) && (
          <div className="stack" style={gap(2, { fontSize: 17 })}>
            {place && <strong>{place}</strong>}
            {store.landmark && <span className="muted">{store.landmark}</span>}
          </div>
        )}
        <div className="wrap-row" style={gap(8)}>
          {store.defaultPickupStart && store.defaultPickupEnd && (
            <span className="tag">Pickup usually {clockWindow(store.defaultPickupStart, store.defaultPickupEnd)}</span>
          )}
          <span className="tag">{store.gcashAccepted ? 'Cash or GCash at the counter' : 'Cash at the counter'}</span>
          {stats.usualPostTime && <span className="tag">Posts around {clockOf(stats.usualPostTime)}</span>}
        </div>
        <div className="wrap-row" style={{ alignItems: 'center' }}>
          {(isBuyer || !loggedIn) && (
            <button type="button" className={`btn${follow?.following ? '' : ' lime'}`} aria-pressed={follow?.following ?? false}
              disabled={followBusy} onClick={toggleFollow}>
              {follow?.following ? 'Following' : 'Follow'}
            </button>
          )}
          {place && (
            <a className="btn" href={`https://www.google.com/maps/search/?api=1&query=${mapQuery}`} target="_blank" rel="noreferrer">
              Open in Google Maps
            </a>
          )}
        </div>
        {followProblem && <p role="alert" className="err">{followProblem}</p>}
        {(isBuyer || !loggedIn) && (
          <p className="small" role="status">
            {!follow?.following ? `Follow ${store.name} to get an alert when it posts food.`
              : follow.alertsOn ? `You’ll get an alert when ${store.name} posts food. Tap Following to stop.`
              : <>You follow {store.name} quietly. Turn its alerts on from <a href={href(paths.following)}>Following</a>.</>}
          </p>
        )}
        </div>
      </section>

      {record.length > 0 && (
        <section aria-label="Track record" className={record.length === 3 ? 'three' : record.length === 2 ? 'two' : undefined}>
          {record.map((entry) => (
            <div key={entry.label} className="card kpi">
              <strong className="wide value num">{entry.value}</strong>
              <span>{entry.label}</span>
            </div>
          ))}
        </section>
      )}

      <section className="stack" style={gap(16)}>
        <div className="spread" style={{ flexWrap: 'wrap' }}>
          <div className="row">
            <h2 className="wide" style={{ fontSize: 28 }}>On sale now</h2>
            <LiveBadge what="Stock counts" />
          </div>
          <p className="muted">{items.length === 0 ? '' : onSale === 0 ? 'All sold out' : `${plural(onSale, 'listing')} to reserve`}</p>
        </div>
        {problem && <p role="alert" className="err">{problem}</p>}
        {items.length === 0 ? (
          <div className="panel cream stack" style={gap(6, { padding: '20px 24px' })}>
            <strong style={{ fontSize: 18 }}>Nothing for pickup right now.</strong>
            <p>
              {stats.usualPostTime
                ? `${store.name} usually posts around ${clockOf(stats.usualPostTime)}. Check back then.`
                : `${store.name} hasn’t posted food yet today.`}
            </p>
            <div><a className="btn" href={href(paths.browse)}>See other stores’ deals</a></div>
          </div>
        ) : (
          <div className="deals">
            {items.map((item) => (
              <DealCard key={item.id} item={item} now={now} inCart={inCart.get(item.id)} canReserve={canReserve}
                loggedIn={loggedIn} adding={adding === item.id} onAdd={() => add(item)} flashes={changes[item.id]} hideStore />
            ))}
          </div>
        )}
      </section>

      {store.hours.length > 0 && (
        <section className="card" style={{ maxWidth: 420 }}>
          <div className="card-head"><h2 style={{ fontSize: 18, fontWeight: 800 }}>Opening hours</h2></div>
          <table className="tbl">
            <tbody>
              {(['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'] as const).map((weekday) => {
                const hours = store.hours.find((h) => h.weekday === weekday)
                const today = weekday === weekdayOf(manilaDate(now))
                return (
                  <tr key={weekday} style={{ fontWeight: today ? 800 : undefined }}>
                    <th scope="row" style={{ border: 0, borderBottom: '1px solid var(--rule)', fontSize: 16, color: 'inherit', fontWeight: 'inherit' }}>
                      {dayName(weekday)}{today && <span className="sr"> (today)</span>}
                    </th>
                    <td className="r num">{hours ? clockWindow(hours.opens, hours.closes) : 'Closed'}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </section>
      )}

      <section className="panel cream stack" style={gap(6, { padding: '18px 20px' })}>
        <strong>About the food</strong>
        <p>{store.name} made this food and is responsible for it. Ask at the counter about allergens before you pay.</p>
        {isBuyer && (
          <p className="small">
            Something wrong with this store? <a href={href(paths.reportStore(store.slug))}>Report a problem</a>
          </p>
        )}
      </section>

      {mine.length > 0 && (
        <div style={{ position: 'sticky', bottom: 16, zIndex: 5 }} className="cart-bar">
          <div className="card spread" style={{ background: 'var(--ink)', color: '#fff', boxShadow: '5px 5px 0 var(--teal)', padding: '12px 16px 12px 22px' }}>
            <p><strong>{plural(mine.length, 'item')} from {store.name} in your cart.</strong>{' '}
              <span style={{ color: '#CDE3E2' }}>Pay {peso(mine.reduce((sum, line) => sum + line.lineTotal, 0))} at pickup.</span></p>
            <a className="btn lime sm" href={href(paths.cart)} style={{ boxShadow: '2px 2px 0 #fff' }}>Review cart</a>
          </div>
        </div>
      )}
    </div>
  )
}
