import { useCallback, useState, type FormEvent } from 'react'
import { ApiError, api, photoUrl } from '../api/client'
import { PhotoPicker } from '../components/PhotoPicker'
import type { StoreProfile, Weekday } from '../api/types'
import { Loading, ProblemPanel } from '../components/States'
import { messageOf } from '../lib/errors'
import { clockWindow } from '../lib/format'
import { href, paths } from '../lib/route'
import { gap } from '../lib/style'
import { useLoad } from '../lib/useLoad'

const WEEK: Weekday[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']
const dayName = (weekday: Weekday) => weekday.charAt(0) + weekday.slice(1).toLowerCase()

interface DayDraft { open: boolean; opens: string; closes: string }

interface Draft {
  name: string
  address: string
  barangay: string
  landmark: string
  gcash: boolean
  pickupStart: string
  pickupEnd: string
  hours: Record<Weekday, DayDraft>
}

/** "07:00:00" from the API -> "07:00" for a time box. */
const hhmm = (time: string | null) => (time ? time.slice(0, 5) : '')

function draftOf(store: StoreProfile): Draft {
  const hours = Object.fromEntries(WEEK.map((weekday) => {
    const saved = store.hours.find((h) => h.weekday === weekday)
    return [weekday, saved
      ? { open: true, opens: hhmm(saved.opens), closes: hhmm(saved.closes) }
      : { open: false, opens: '07:00', closes: '19:00' }]
  })) as Record<Weekday, DayDraft>
  return {
    name: store.name, address: store.address ?? '', barangay: store.barangay ?? '', landmark: store.landmark ?? '',
    gcash: store.gcashAccepted, pickupStart: hhmm(store.defaultPickupStart), pickupEnd: hhmm(store.defaultPickupEnd), hours,
  }
}

function problemsOf(d: Draft): Record<string, string> {
  const p: Record<string, string> = {}
  if (!d.name.trim()) p.name = 'Your store needs a name.'
  for (const weekday of WEEK) {
    const day = d.hours[weekday]
    if (day.open && !(day.opens && day.closes && day.opens < day.closes)) p[weekday] = 'Closing time has to be after opening time.'
  }
  if (Boolean(d.pickupStart) !== Boolean(d.pickupEnd)) p.pickup = 'Fill in both pickup times, or leave both empty.'
  else if (d.pickupStart && d.pickupStart >= d.pickupEnd) p.pickup = 'Pickup has to end after it starts.'
  return p
}

/** What buyers see on the store's page, and the defaults for new listings. */
export function StoreProfilePage() {
  const loaded = useLoad(useCallback(() => api.myStore(), []))
  if (loaded.error && !loaded.data) return <ProblemPanel error={loaded.error} onRetry={loaded.reload} />
  if (!loaded.data) return <Loading label="Loading your store profile…" />
  // The key starts the form over from the saved profile whenever a newer one is loaded.
  return <ProfileForm key={loaded.data.version} store={loaded.data} onReload={loaded.reload} />
}

function ProfileForm({ store, onReload }: { store: StoreProfile; onReload: () => void }) {
  const [saved, setSaved] = useState(store)
  const [draft, setDraft] = useState(() => draftOf(store))
  const [photoId, setPhotoId] = useState(store.photoId)
  const [touched, setTouched] = useState(false)
  const [busy, setBusy] = useState(false)
  const [done, setDone] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [stale, setStale] = useState(false)
  const problems = problemsOf(draft)
  const shown = touched ? problems : {}

  const change = (patch: Partial<Draft>) => { setDraft({ ...draft, ...patch }); setDone(false) }
  const changeDay = (weekday: Weekday, patch: Partial<DayDraft>) =>
    change({ hours: { ...draft.hours, [weekday]: { ...draft.hours[weekday], ...patch } } })
  const text = (key: 'name' | 'address' | 'barangay' | 'landmark', maxLength: number) => ({
    id: key, className: `input${shown[key] ? ' bad' : ''}`, maxLength, value: draft[key],
    onChange: (e: { target: { value: string } }) => change({ [key]: e.target.value }),
  })

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setTouched(true)
    setError(null)
    if (Object.keys(problems).length > 0) return
    setBusy(true)
    try {
      const next = await api.saveStore({
        name: draft.name.trim(),
        address: draft.address.trim() || null,
        barangay: draft.barangay.trim() || null,
        landmark: draft.landmark.trim() || null,
        gcashAccepted: draft.gcash,
        defaultPickupStart: draft.pickupStart || null,
        defaultPickupEnd: draft.pickupEnd || null,
        hours: WEEK.filter((weekday) => draft.hours[weekday].open)
          .map((weekday) => ({ weekday, opens: draft.hours[weekday].opens, closes: draft.hours[weekday].closes })),
        version: saved.version,
        photoId,
      })
      setSaved(next)
      setDone(true)
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) setStale(true)
      else setError(messageOf(err))
    } finally {
      setBusy(false)
    }
  }

  const place = [draft.address.trim(), draft.barangay.trim()].filter(Boolean).join(', ')
  const openDays = WEEK.filter((weekday) => draft.hours[weekday].open)

  return (
    <div className="stack" style={gap(24)}>
      <div className="page-head">
        <div className="stack" style={gap(6)}>
          <h1 className="wide">Store profile</h1>
          <p className="muted">What buyers see on your store page, and the defaults for new listings.</p>
        </div>
        <a className="btn" href={href(paths.store(saved.slug))}>See your store page</a>
      </div>

      <div className="cart-layout">
        <form className="stack" style={gap(24)} onSubmit={submit} noValidate>
          <section className="card stack" style={gap(16, { padding: 24 })}>
            <h2 className="wide" style={{ fontSize: 22 }}>Where to find you</h2>
            <div className="field">
              <label htmlFor="name">Store name</label>
              <input {...text('name', 80)} aria-invalid={shown.name ? true : undefined} aria-describedby={shown.name ? 'name-err' : undefined} />
              {shown.name && <span id="name-err" className="err">{shown.name}</span>}
            </div>
            <PhotoPicker id="store-photo" label="Store photo (optional)" photoId={photoId}
              onChange={(next) => { setPhotoId(next); setDone(false) }}
              hint="Your storefront or counter, so buyers know what to look for. Shown at the top of your store page." />
            <div className="two" style={{ gap: 14 }}>
              <div className="field">
                <label htmlFor="address">Street address</label>
                <input {...text('address', 200)} placeholder="112 Session Road" />
              </div>
              <div className="field">
                <label htmlFor="barangay">Barangay</label>
                <input {...text('barangay', 80)} placeholder="Session Road Area" />
              </div>
            </div>
            <div className="field">
              <label htmlFor="landmark">Landmark</label>
              <input {...text('landmark', 200)} placeholder="Green awning, two doors up from the jeepney stop" aria-describedby="landmark-hint" />
              <span id="landmark-hint" className="hint">Helps buyers who don’t know the street. Shown under your address.</span>
            </div>
          </section>

          <section className="card stack" style={gap(12, { padding: 24 })}>
            <h2 className="wide" style={{ fontSize: 22 }}>Opening hours</h2>
            {WEEK.map((weekday) => {
              const day = draft.hours[weekday]
              return (
                <div key={weekday} className="stack" style={gap(4)}>
                  <div className="hours-row">
                    <label className="row" style={gap(8, { cursor: 'pointer', fontWeight: 600 })}>
                      <input type="checkbox" checked={day.open} onChange={(e) => changeDay(weekday, { open: e.target.checked })}
                        style={{ width: 18, height: 18, accentColor: 'var(--ink)' }} />
                      {dayName(weekday)}
                    </label>
                    {day.open ? (
                      <>
                        <input className={`input${shown[weekday] ? ' bad' : ''}`} type="time" value={day.opens}
                          aria-label={`${dayName(weekday)} opens at`} onChange={(e) => changeDay(weekday, { opens: e.target.value })} />
                        <span className="muted">to</span>
                        <input className={`input${shown[weekday] ? ' bad' : ''}`} type="time" value={day.closes}
                          aria-label={`${dayName(weekday)} closes at`} onChange={(e) => changeDay(weekday, { closes: e.target.value })} />
                      </>
                    ) : <span className="muted">Closed all day</span>}
                  </div>
                  {shown[weekday] && <span role="alert" className="err">{shown[weekday]}</span>}
                </div>
              )
            })}
          </section>

          <section className="card stack" style={gap(16, { padding: 24 })}>
            <h2 className="wide" style={{ fontSize: 22 }}>Defaults for new listings</h2>
            <div className="two" style={{ gap: 14 }}>
              <div className="field">
                <label htmlFor="pickupStart">Pickup from</label>
                <input id="pickupStart" className={`input${shown.pickup ? ' bad' : ''}`} type="time" value={draft.pickupStart}
                  onChange={(e) => change({ pickupStart: e.target.value })} />
              </div>
              <div className="field">
                <label htmlFor="pickupEnd">Pickup until</label>
                <input id="pickupEnd" className={`input${shown.pickup ? ' bad' : ''}`} type="time" value={draft.pickupEnd}
                  onChange={(e) => change({ pickupEnd: e.target.value })} />
              </div>
            </div>
            {shown.pickup
              ? <span role="alert" className="err">{shown.pickup}</span>
              : <span className="hint">New listings and “Post again” start with these times. You can still change them each time.</span>}
            <fieldset className="stack" style={gap(8, { border: 0, margin: 0, padding: 0 })}>
              <legend style={{ fontWeight: 700, marginBottom: 8 }}>Buyers can pay with</legend>
              <label className="row" style={gap(8)}>
                <input type="checkbox" checked disabled style={{ width: 18, height: 18 }} /> Cash <span className="hint">(always on)</span>
              </label>
              <label className="row" style={gap(8, { cursor: 'pointer' })}>
                <input type="checkbox" checked={draft.gcash} onChange={(e) => change({ gcash: e.target.checked })}
                  style={{ width: 18, height: 18, accentColor: 'var(--ink)' }} /> GCash
              </label>
            </fieldset>
          </section>

          {stale && (
            <div role="alert" className="panel notice bad">
              <p className="grow"><strong>Someone else changed the profile while you were editing.</strong> Load the latest, then make your changes again.</p>
              <button type="button" className="btn sm" onClick={onReload}>Load the latest</button>
            </div>
          )}
          {error && <p role="alert" className="err">{error}</p>}
          {touched && Object.keys(problems).length > 0 && <p role="alert" className="err">Fix the marked boxes above, then save.</p>}
          <div className="wrap-row" style={{ alignItems: 'center' }}>
            <button type="submit" className="btn teal big" disabled={busy || stale}>{busy ? 'Saving…' : 'Save changes'}</button>
            {done && <span role="status" style={{ fontWeight: 700 }}>Changes saved. Your store page shows them now.</span>}
          </div>
        </form>

        <aside className="stack" style={gap(12)} aria-label="Preview">
          <strong>How buyers will see it</strong>
          <div className="card cream stack" style={gap(8, { padding: 20 })}>
            {photoId && <div className="photo shot store-shot"><img src={photoUrl(photoId, 'thumb')} alt="" /></div>}
            <h3 className="wide" style={{ fontSize: 26, lineHeight: '30px' }}>{draft.name.trim() || 'Your store'}</h3>
            {place ? <strong>{place}</strong> : <span className="muted">Add your address so buyers can find you.</span>}
            {draft.landmark.trim() && <span className="muted">{draft.landmark.trim()}</span>}
            <div className="wrap-row" style={gap(6, { marginTop: 6 })}>
              {draft.pickupStart && draft.pickupEnd && draft.pickupStart < draft.pickupEnd && (
                <span className="tag">Pickup usually {clockWindow(draft.pickupStart, draft.pickupEnd)}</span>
              )}
              <span className="tag">{draft.gcash ? 'Cash or GCash at the counter' : 'Cash at the counter'}</span>
            </div>
            <p className="small muted" style={{ marginTop: 6 }}>
              {openDays.length === 0 ? 'No opening hours set.' : `Open ${openDays.length === 7 ? 'every day' : `${openDays.length} days a week`}.`}
            </p>
          </div>
        </aside>
      </div>
    </div>
  )
}
