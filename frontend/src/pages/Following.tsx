import { useCallback, useState } from 'react'
import { api } from '../api/client'
import type { AlertItem, AlertKind, FollowedStore } from '../api/types'
import { Loading, ProblemPanel } from '../components/States'
import { messageOf } from '../lib/errors'
import { addDays, clockOf, dayOf, manilaDate, plural, timeOf } from '../lib/format'
import type { LiveMessage } from '../lib/live'
import { href, navigate, paths } from '../lib/route'
import { gap } from '../lib/style'
import { useLiveReload } from '../lib/useLive'
import { useLoad, useNow } from '../lib/useLoad'

const alertsChanged = (message: LiveMessage) => message.type === 'alerts'

const kinds: Record<AlertKind, { label: string; tag: string; action: string }> = {
  PICKUP: { label: 'Pickup', tag: 'orange', action: 'Show claim code' },
  DEAL: { label: 'New deal', tag: 'lime', action: 'See the deals' },
  PICKED_UP: { label: 'Picked up', tag: 'cream', action: 'See reservation' },
  MISSED: { label: 'Missed', tag: '', action: 'See your pickup record' },
  REPORT: { label: 'Report answered', tag: 'teal', action: 'See your reports' },
}
const tints = ['var(--lime)', 'var(--orange)', 'var(--cream)']

/** "Today", "Yesterday" or "Nov 4", in Manila time. */
function dayHeading(iso: string, now: number): string {
  const day = manilaDate(new Date(iso).getTime())
  const today = manilaDate(now)
  return day === today ? 'Today' : day === addDays(today, -1) ? 'Yesterday' : dayOf(iso)
}

/** Alerts from the stores you follow and about your own pickups, and the list of stores you follow. */
export function Following() {
  const now = useNow()
  const loaded = useLoad(useCallback(() => Promise.all([api.alerts(), api.follows()]), []))
  useLiveReload(loaded.reload, alertsChanged)
  const [problem, setProblem] = useState<string | null>(null)

  if (loaded.error && !loaded.data) return <ProblemPanel error={loaded.error} onRetry={loaded.reload} />
  if (!loaded.data) return <Loading label="Loading your alerts…" />
  const [{ unread, alerts }, stores] = loaded.data

  // Alerts arrive newest first; keep that order and put a heading where the day changes.
  const groups: { day: string; items: AlertItem[] }[] = []
  for (const alert of alerts) {
    const day = dayHeading(alert.createdAt, now)
    const last = groups[groups.length - 1]
    if (last && last.day === day) last.items.push(alert)
    else groups.push({ day, items: [alert] })
  }

  const run = async (action: () => Promise<unknown>) => {
    setProblem(null)
    try {
      await action()
      loaded.reload()
    } catch (error) {
      setProblem(messageOf(error))
    }
  }
  const open = (alert: AlertItem) => {
    // Reading it shouldn't hold up going there; if marking fails, it just stays unread.
    if (!alert.read) api.readAlert(alert.id).catch(() => undefined)
    navigate(alert.link)
  }

  return (
    <div className="stack" style={gap(28, { maxWidth: 820 })}>
      <div className="page-head">
        <div className="stack" style={gap(6)}>
          <h1 className="wide">Alerts</h1>
          <p className="muted" aria-live="polite">
            {unread === 0 ? 'You’re all caught up.' : `${plural(unread, 'new alert')} since you last looked.`}
          </p>
        </div>
        {unread > 0 && <button type="button" className="btn" onClick={() => run(() => api.readAllAlerts())}>Mark all as read</button>}
      </div>
      {problem && <p role="alert" className="err">{problem}</p>}

      {alerts.length === 0 ? (
        <p className="panel cream" style={{ padding: '18px 20px' }}>
          No alerts yet. When a store you follow posts food, or something happens with one of your pickups, it shows up here.
        </p>
      ) : groups.map((group) => (
        <section key={group.day} className="stack" style={gap(10)}>
          <h2 className="wide" style={{ fontSize: 20 }}>{group.day}</h2>
          <ul className="card" style={{ listStyle: 'none', margin: 0, padding: 0 }}>
            {group.items.map((alert) => (
              <li key={alert.id} className="card-row alert-row" style={{ background: alert.read ? undefined : 'var(--glow)' }}>
                <span className={`tag ${kinds[alert.kind].tag}`}>{kinds[alert.kind].label}</span>
                <div className="stack" style={gap(4)}>
                  <div className="spread" style={{ alignItems: 'baseline' }}>
                    <strong>{!alert.read && <span className="sr">New: </span>}{alert.title}</strong>
                    <span className="hint num" style={{ flex: 'none' }}>{timeOf(alert.createdAt)}</span>
                  </div>
                  <p>{alert.body}</p>
                  <div><button type="button" className="link-btn" onClick={() => open(alert)}>{kinds[alert.kind].action}</button></div>
                </div>
              </li>
            ))}
          </ul>
        </section>
      ))}

      <section className="stack" style={gap(10)}>
        <div className="stack" style={gap(2)}>
          <h2 className="wide" style={{ fontSize: 24 }}>Stores you follow</h2>
          {stores.length > 0 && <p className="muted">You get an alert when they post food.</p>}
        </div>
        {stores.length === 0 ? (
          <div className="panel cream stack" style={gap(10, { padding: '18px 20px' })}>
            <p>You don’t follow any stores yet. Open a store’s page and tap Follow, and you’ll hear when it posts food.</p>
            <div><a className="btn" href={href(paths.browse)}>See tonight’s deals</a></div>
          </div>
        ) : (
          <ul className="card" style={{ listStyle: 'none', margin: 0, padding: 0 }}>
            {stores.map((store, i) => (
              <FollowedRow key={store.storeId} store={store} tint={tints[i % tints.length]}
                onToggle={() => run(() => api.follow(store.slug, !store.alertsOn))}
                onUnfollow={() => run(() => api.unfollow(store.slug))} />
            ))}
          </ul>
        )}
        <p className="hint">Follow a store from its page. Stores usually post in the afternoon, so alerts come before the evening rush.</p>
      </section>
    </div>
  )
}

function FollowedRow({ store, tint, onToggle, onUnfollow }: { store: FollowedStore; tint: string; onToggle: () => void; onUnfollow: () => void }) {
  const about = [store.area, store.usualPostTime && `Usually posts around ${clockOf(store.usualPostTime)}`].filter(Boolean).join('. ')
  return (
    <li className="card-row follow-row">
      <span className="avatar" aria-hidden="true" style={{ background: tint }}>{store.name.charAt(0)}</span>
      <div className="stack grow" style={gap(2)}>
        <a href={href(paths.store(store.slug))} style={{ fontWeight: 700 }}>{store.name}</a>
        {about && <span className="muted small">{about}{about.endsWith('.') ? '' : '.'}</span>}
      </div>
      <div className="row" style={gap(12)}>
        <button type="button" className={`chip${store.alertsOn ? ' on' : ''}`} aria-pressed={store.alertsOn} onClick={onToggle}
          aria-label={`Alerts from ${store.name}`}>
          {store.alertsOn ? 'Alerts on' : 'Alerts off'}
        </button>
        <button type="button" className="link-btn" onClick={onUnfollow} aria-label={`Unfollow ${store.name}`}>Unfollow</button>
      </div>
    </li>
  )
}
