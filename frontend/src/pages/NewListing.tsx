import { useState, type FormEvent, type ReactNode } from 'react'
import { api } from '../api/client'
import { messageOf } from '../lib/errors'
import { clockOf, manilaDate, manilaInstant, peso } from '../lib/format'
import { href, navigate, paths } from '../lib/route'
import { gap } from '../lib/style'
import { useNow } from '../lib/useLoad'

interface Draft {
  name: string
  description: string
  originalPrice: string
  price: string
  quantity: string
  pickupLocation: string
  start: string
  end: string
}

const empty: Draft = {
  name: '', description: '', originalPrice: '', price: '', quantity: '1',
  pickupLocation: '', start: '17:00', end: '19:00',
}

/** Checks the form the same way the API will, so most mistakes show before saving. */
function problemsOf(d: Draft, today: string, now: number): Partial<Record<keyof Draft, string>> {
  const p: Partial<Record<keyof Draft, string>> = {}
  const original = Number(d.originalPrice)
  const price = Number(d.price)
  if (!d.name.trim()) p.name = 'Name the food.'
  if (!(original > 0)) p.originalPrice = 'Enter the usual price.'
  if (d.price === '' || !(price >= 0)) p.price = 'Enter tonight’s price.'
  else if (original > 0 && price > original) p.price = 'Tonight’s price can’t be more than the usual price.'
  if (!(Number(d.quantity) >= 1) || !Number.isInteger(Number(d.quantity))) p.quantity = 'At least 1.'
  if (!d.pickupLocation.trim()) p.pickupLocation = 'Say where buyers pick up.'
  if (d.start >= d.end) p.end = 'Pickup has to end after it starts.'
  else if (new Date(manilaInstant(today, d.end)).getTime() <= now) p.end = 'That time has already passed today.'
  return p
}

export function NewListing() {
  const now = useNow()
  const today = manilaDate(now)
  const [draft, setDraft] = useState<Draft>(empty)
  const [touched, setTouched] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const problems = problemsOf(draft, today, now)
  const shown = touched ? problems : {}
  const set = (key: keyof Draft) => (e: { target: { value: string } }) => setDraft({ ...draft, [key]: e.target.value })

  const original = Number(draft.originalPrice)
  const price = Number(draft.price)
  const off = original > 0 && draft.price !== '' && price <= original ? Math.round((1 - price / original) * 100) : null

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setTouched(true)
    setError(null)
    if (Object.keys(problems).length > 0) return
    setBusy(true)
    try {
      const pickupEnd = manilaInstant(today, draft.end)
      await api.createItem({
        name: draft.name.trim(),
        description: draft.description.trim() || undefined,
        price,
        originalPrice: original,
        quantity: Number(draft.quantity),
        pickupLocation: draft.pickupLocation.trim(),
        pickupStart: manilaInstant(today, draft.start),
        pickupEnd,
        // The listing comes off the app when pickup ends.
        expiresAt: pickupEnd,
      })
      navigate(paths.owner)
    } catch (err) {
      setError(messageOf(err))
      setBusy(false)
    }
  }

  const field = (key: keyof Draft, label: string, input: ReactNode, hint?: string) => (
    <div className="field">
      <label htmlFor={key}>{label}</label>
      {input}
      {shown[key] ? <span id={`${key}-err`} className="err">{shown[key]}</span> : hint && <span className="hint">{hint}</span>}
    </div>
  )
  const attrs = (key: keyof Draft) => ({
    id: key,
    className: `input${shown[key] ? ' bad' : ''}`,
    value: draft[key],
    onChange: set(key),
    'aria-invalid': shown[key] ? true : undefined,
    'aria-describedby': shown[key] ? `${key}-err` : undefined,
  })

  return (
    <div className="stack" style={gap(24)}>
      <div className="page-head">
        <div className="stack" style={gap(6)}>
          <a className="small" href={href(paths.owner)} style={{ fontWeight: 600 }}>Back to today</a>
          <h1 className="wide">Post a listing</h1>
          <p className="muted">For pickup today. It comes off the app when pickup ends.</p>
        </div>
      </div>

      <div className="cart-layout">
        <form className="card stack" style={gap(18, { padding: 24 })} onSubmit={submit} noValidate>
          {field('name', 'Food', <input {...attrs('name')} maxLength={120} placeholder="Ensaymada, box of 6" />)}
          {field('description', 'Details (optional)', <textarea {...attrs('description')} maxLength={500} rows={3}
            placeholder="Baked this morning. Ube and cheese." style={{ resize: 'vertical', padding: '10px 14px', height: 'auto' }} />)}
          <div className="three" style={{ gap: 14 }}>
            {field('originalPrice', 'Usual price (₱)', <input {...attrs('originalPrice')} type="number" inputMode="decimal" min={0.01} step={0.01} />)}
            {field('price', 'Tonight’s price (₱)', <input {...attrs('price')} type="number" inputMode="decimal" min={0} step={0.01} />)}
            {field('quantity', 'How many', <input {...attrs('quantity')} type="number" inputMode="numeric" min={1} max={10000} step={1} />)}
          </div>
          {field('pickupLocation', 'Pickup at', <input {...attrs('pickupLocation')} maxLength={200} placeholder="Front counter, 12 Legarda Rd" />)}
          <div className="two" style={{ gap: 14 }}>
            {field('start', 'Pickup from', <input {...attrs('start')} type="time" />)}
            {field('end', 'Pickup until', <input {...attrs('end')} type="time" />, 'Manila time')}
          </div>
          {error && <p role="alert" className="err">{error}</p>}
          <div className="wrap-row">
            <button type="submit" className="btn teal big" disabled={busy}>{busy ? 'Posting…' : 'Post listing'}</button>
            <a className="btn big" href={href(paths.owner)}>Cancel</a>
          </div>
        </form>

        <aside className="stack" style={gap(12)} aria-label="Preview">
          <strong>How buyers will see it</strong>
          <article className="card deal">
            <div className="photo">
              <span>Photo</span>
              {off != null && off > 0 && <span className="tag lime tilt">{off}% off</span>}
            </div>
            <div className="deal-body">
              <h3>{draft.name.trim() || 'Your food'}</h3>
              <p className="price">
                <strong className="wide num">{draft.price !== '' && price >= 0 ? peso(price) : '₱–'}</strong>
                {original > 0 && <s className="muted num small">{peso(original)}</s>}
              </p>
              <div className="wrap-row" style={gap(6)}>
                <span className="tag">{Number(draft.quantity) || 0} left</span>
              </div>
              <p className="muted small">Pick up {draft.start && clockOf(draft.start)} to {draft.end && clockOf(draft.end)}</p>
            </div>
          </article>
        </aside>
      </div>
    </div>
  )
}
