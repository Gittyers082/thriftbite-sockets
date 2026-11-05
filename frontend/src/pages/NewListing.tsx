import { useState, type FormEvent } from 'react'
import { Shell } from '../components/Shell'
import { STORE } from '../data/sample'
import { peso, percentOff } from '../lib/format'
import { href, navigate } from '../lib/route'

export function NewListing() {
  const [name, setName] = useState('Pandesal (bag of 10)')
  const [usual, setUsual] = useState('50.00')
  const [price, setPrice] = useState('25.00')
  const [qty, setQty] = useState('6')

  const usualN = parseFloat(usual) || 0
  const priceN = parseFloat(price) || 0
  const tooHigh = priceN > usualN
  const off = percentOff(priceN, usualN)

  // Draft: no API call yet. Wire to POST /api/owner/items (src/api/client.ts).
  const publish = (e: FormEvent) => {
    e.preventDefault()
    if (!tooHigh) navigate('/owner')
  }

  return (
    <Shell area="owner" current="/owner/new" counts={{ '/owner': '2 pickups' }}
      topbar={
        <div className="topbar crumbs">
          <a href={href('/owner')}>Today</a><span aria-hidden="true">/</span><span style={{ color: 'var(--ink)' }}>New listing</span>
        </div>
      }>
      <form onSubmit={publish} style={{ maxWidth: 1152, display: 'flex', flexDirection: 'column' }}>
        <div className="titles stack" style={{ gap: 4, paddingBottom: 24 }}>
          <h1 className="title">New listing</h1>
          <p className="subline">List food you have left today. Buyers can reserve it as soon as you publish.</p>
        </div>

        <section className="form-section" aria-labelledby="s1">
          <div>
            <h2 id="s1">The food</h2>
            <p className="hint">Name it the way it's labelled on your counter, with the pack size.</p>
          </div>
          <div className="panel">
            <div className="field">
              <label htmlFor="name">Name</label>
              <input id="name" className="input" required maxLength={120} value={name} onChange={(e) => setName(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="desc">Description <span className="muted" style={{ fontWeight: 400 }}>(optional)</span></label>
              <textarea id="desc" className="input" rows={2} maxLength={500} defaultValue="Baked this morning. Best toasted." />
            </div>
          </div>
        </section>

        <section className="form-section" aria-labelledby="s2">
          <div>
            <h2 id="s2">Price and stock</h2>
            <p className="hint">Buyers pay a portion of the usual price. At these numbers they see it as <strong className="num" style={{ color: 'var(--ink)' }}>{off}% off</strong>.</p>
          </div>
          <div className="panel cols-3">
            <div className="field">
              <label htmlFor="usual">Usual price (₱)</label>
              <input id="usual" className="input num" inputMode="decimal" required value={usual} onChange={(e) => setUsual(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="price">ThriftBite price (₱)</label>
              <input id="price" className={`input num${tooHigh ? ' invalid' : ''}`} inputMode="decimal" required value={price}
                onChange={(e) => setPrice(e.target.value)} aria-invalid={tooHigh} aria-describedby={tooHigh ? 'price-err' : undefined} />
              {tooHigh && <span id="price-err" role="alert" className="error">Can't be higher than the usual price.</span>}
            </div>
            <div className="field">
              <label htmlFor="qty">How many</label>
              <input id="qty" className="input num" type="number" min={1} max={10000} required value={qty} onChange={(e) => setQty(e.target.value)} />
            </div>
          </div>
        </section>

        <section className="form-section" aria-labelledby="s3">
          <div>
            <h2 id="s3">Pickup</h2>
            <p className="hint">Pickup has to end before the food expires. Buyers who miss the window lose the reservation, and the food goes back on sale.</p>
          </div>
          <div className="panel">
            <div className="field">
              <label htmlFor="where">Where to pick up</label>
              <input id="where" className="input" required defaultValue={`${STORE.name} counter, ${STORE.area}`} />
            </div>
            <div className="cols-3">
              <div className="field"><label htmlFor="from">Pickup from</label><input id="from" className="input" type="datetime-local" required defaultValue="2026-11-05T17:00" /></div>
              <div className="field"><label htmlFor="until">Pickup until</label><input id="until" className="input" type="datetime-local" required defaultValue="2026-11-05T19:00" /></div>
              <div className="field"><label htmlFor="exp">Food expires</label><input id="exp" className="input" type="datetime-local" required defaultValue="2026-11-05T21:00" /></div>
            </div>
          </div>
        </section>

        <section className="form-section" aria-labelledby="s4">
          <div>
            <h2 id="s4">How buyers see it</h2>
            <p className="hint">This is the row in Today's surplus. It updates as you type.</p>
          </div>
          <div className="stack" style={{ gap: 16 }}>
            <div className="card" style={{ padding: '12px 16px', display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) auto auto', gap: 24, alignItems: 'center' }}>
              <div className="food" style={{ minWidth: 0 }}>
                <div className="thumb" aria-hidden="true">Photo</div>
                <div className="stack" style={{ minWidth: 0 }}>
                  <span className="name" style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{name || 'Untitled'}</span>
                  <span className="small muted">{STORE.name} · {STORE.area}</span>
                </div>
              </div>
              <div className="stack">
                <span>5:00–7:00 PM</span>
                <span className="small muted num">{qty || 0} left</span>
              </div>
              <div className="stack end num">
                <span className="price">{peso(priceN)}</span>
                <span className="small muted"><s>{peso(usualN)}</s> <span className="off">{off}% off</span></span>
              </div>
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: 20 }}>
              <a href={href('/owner')} style={{ fontWeight: 600, textDecoration: 'none' }}>Cancel</a>
              <button type="submit" className="btn-primary" disabled={tooHigh}>Publish listing</button>
            </div>
          </div>
        </section>
      </form>
    </Shell>
  )
}
