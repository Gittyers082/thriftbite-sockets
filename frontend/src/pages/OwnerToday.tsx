import { useState, type FormEvent } from 'react'
import { Icon } from '../components/Icon'
import { Shell } from '../components/Shell'
import { SellOutRow } from '../components/Timeline'
import { listings, pickups as samplePickups, sellOuts, STORE, tints, type ListingStatus, type Pickup } from '../data/sample'
import { clock, peso, plural, untilLabel } from '../lib/format'
import { href } from '../lib/route'

const statusPill: Record<ListingStatus, { label: string; tone: string }> = {
  AVAILABLE: { label: 'For sale', tone: 'sale' },
  SOLD_OUT: { label: 'Sold out', tone: 'neutral' },
  EXPIRED: { label: 'Expired', tone: 'warn' },
}

export function OwnerToday() {
  const [pickups, setPickups] = useState<Pickup[]>(samplePickups)
  const [codes, setCodes] = useState<Record<string, string>>({})

  // Draft: checks the code locally. Wire to POST /api/owner/reservations/{id}/complete.
  const handOver = (e: FormEvent, p: Pickup) => {
    e.preventDefault()
    const ok = (codes[p.id] ?? '').replace(/\s/g, '') === p.claimCode
    setPickups((ps) => ps.map((x) => (x.id !== p.id ? x : ok ? { ...x, state: 'done', doneAt: '5:06 PM' } : { ...x, state: 'wrong' })))
  }

  const waiting = pickups.filter((p) => p.state !== 'done')
  const done = pickups.filter((p) => p.state === 'done')
  const toCollect = waiting.reduce((s, p) => s + p.total, 0)
  const collected = done.reduce((s, p) => s + p.total, 0)
  const forSale = listings.filter((l) => l.status === 'AVAILABLE')
  const unitsForSale = forSale.reduce((s, l) => s + l.left, 0)
  const sellOut = sellOuts[0]
  const earlier = Math.round((sellOut.lastSunday - sellOut.today) * 60)

  const kpis = [
    { label: 'Reserved today', value: peso(toCollect + collected), note: plural(pickups.length, 'reservation') },
    { label: 'Waiting for pickup', value: String(waiting.length), note: `${peso(toCollect)} to collect` },
    { label: 'Picked up', value: String(done.length), note: `${peso(collected)} collected` },
    { label: 'Still for sale', value: plural(unitsForSale, 'unit'), note: `across ${plural(forSale.length, 'listing')}` },
  ]

  return (
    <Shell area="owner" current="/owner" counts={{ '/owner': plural(waiting.length, 'pickup') }}>
      <div className="page-head" style={{ marginBottom: 4 }}>
        <div className="titles">
          <h1 className="title">Today at {STORE.name}</h1>
          <p className="subline">
            {plural(waiting.length, 'pickup')} due by 7:00 PM with {peso(toCollect)} still to collect.
            {' '}{sellOut.name.split(' (')[0]} sold out {earlier} m earlier than last Sunday.
          </p>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <span className="stamp" style={{ paddingTop: 0 }}>Updated 5:05 PM</span>
          <a className="btn-primary" href={href('/owner/new')} style={{ minHeight: 36 }}><Icon name="plus" /> New listing</a>
        </div>
      </div>

      <section aria-label="Today in numbers" className="card" style={{ padding: 0, display: 'grid', gridTemplateColumns: 'repeat(4, minmax(0, 1fr))', gap: 0 }}>
        {kpis.map((k, i) => (
          <div key={k.label} className="stack" style={{ padding: '16px 20px', gap: 4, borderLeft: i === 0 ? 0 : '1px solid var(--border)' }}>
            <span className="small muted">{k.label}</span>
            <span className="num" style={{ fontSize: 24, lineHeight: '30px', fontWeight: 600 }}>{k.value}</span>
            <span className="small muted">{k.note}</span>
          </div>
        ))}
      </section>

      {/* One 16px gutter: 3fr + 2fr closes on the sheet's edges. */}
      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 3fr) minmax(0, 2fr)', gap: 16 }}>
        <section className="card" aria-labelledby="pk-h" style={{ gap: 12 }}>
          <div className="card-head">
            <h2 id="pk-h" className="card-title">Pickups</h2>
            <span className="small muted">Due by 7:00 PM</span>
          </div>
          <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
            {pickups.map((p, i) => (
              <li key={p.id} style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: '12px 20px', padding: '14px 0', borderTop: '1px solid var(--rule)' }}>
                <span className="avatar lg" aria-hidden="true" style={{ background: tints[i % tints.length].bg, color: tints[i % tints.length].ink }}>
                  {p.buyer.charAt(0).toUpperCase()}
                </span>
                <div className="stack" style={{ flex: '1 1 240px', minWidth: 0 }}>
                  <span style={{ fontWeight: 600 }}>{p.buyer} <span className="num muted" style={{ fontWeight: 400 }}>· {peso(p.total)}</span></span>
                  <span className="small muted">{p.items}</span>
                </div>
                {p.state === 'done' ? (
                  <span className="pill good">Picked up {p.doneAt}</span>
                ) : (
                  <form className="stack" style={{ gap: 4 }} onSubmit={(e) => handOver(e, p)}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <label htmlFor={`code-${p.id}`} className="sr-only">Claim code for {p.buyer}</label>
                      <input id={`code-${p.id}`} className={`input num${p.state === 'wrong' ? ' invalid' : ''}`}
                        style={{ width: 132, height: 36, fontSize: 16, letterSpacing: 3 }}
                        inputMode="numeric" maxLength={7} placeholder="Code" autoComplete="off"
                        value={codes[p.id] ?? ''} onChange={(e) => setCodes((c) => ({ ...c, [p.id]: e.target.value }))}
                        aria-invalid={p.state === 'wrong'} aria-describedby={p.state === 'wrong' ? `err-${p.id}` : undefined} />
                      <button type="submit" className="btn-ghost md">Hand over</button>
                    </div>
                    {p.state === 'wrong' && <span id={`err-${p.id}`} role="alert" className="error">Wrong claim code. Ask to see it again.</span>}
                  </form>
                )}
              </li>
            ))}
          </ul>
        </section>

        <section className="card" aria-labelledby="so-h">
          <div className="card-head">
            <h2 id="so-h" className="card-title">Sold out today</h2>
            <span style={{ display: 'inline-flex', gap: 14, fontSize: 12 }} className="muted">
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}><span aria-hidden="true" style={{ width: 8, height: 8, borderRadius: 999, background: 'var(--teal)' }} />Today</span>
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}><span aria-hidden="true" style={{ width: 2, height: 10, background: 'var(--muted)' }} />Last Sunday</span>
            </span>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) 116px', columnGap: 16 }}>
            <SellOutRow name={sellOut.name} today={sellOut.today} compare={sellOut.lastSunday} opens={STORE.opens} closes={STORE.closes} />
            <div className="stack end num">
              <span style={{ fontWeight: 600 }}>{clock(sellOut.today)}</span>
              <span className="muted" style={{ fontSize: 12, textAlign: 'right' }}>{earlier} m earlier than last Sunday</span>
            </div>
          </div>
          <p className="small muted" style={{ borderTop: '1px solid var(--rule)', paddingTop: 12 }}>
            Sold out {untilLabel(sellOut.today, STORE.closes)} before you close at {clock(STORE.closes)}. Pandesal, ensaymada and Spanish bread are still for sale.
          </p>
          <p className="small" style={{ color: 'var(--ink-2)' }}>Selling out this early often means there's room to list a few more tomorrow.</p>
        </section>
      </div>

      <section className="card" aria-labelledby="ls-h" style={{ paddingBottom: 8, gap: 12 }}>
        <div className="card-head">
          <h2 id="ls-h" className="card-title">Listings</h2>
          <span className="small muted">{plural(listings.length, 'listing')} · {plural(unitsForSale, 'unit')} still for sale</span>
        </div>
        <table className="table">
          <thead>
            <tr>
              <th scope="col">Food</th><th scope="col" className="num">Price</th><th scope="col" className="num">Left</th>
              <th scope="col" className="num">Expires</th><th scope="col">Status</th><th scope="col"><span className="sr-only">Edit</span></th>
            </tr>
          </thead>
          <tbody>
            {listings.map((l) => (
              <tr key={l.id}>
                <td style={{ fontWeight: 600 }}>{l.name}</td>
                <td className="num"><span style={{ fontWeight: 600 }}>{peso(l.price)}</span> <s className="muted">{peso(l.usual)}</s></td>
                <td className="num">{l.left} <span className="muted">of {l.listed}</span></td>
                <td className="num">{l.expires}</td>
                <td><span className={`pill ${statusPill[l.status].tone}`}>{statusPill[l.status].label}</span></td>
                <td className="end"><a href={href('/owner/new')} style={{ display: 'inline-flex', alignItems: 'center', minHeight: 36, fontWeight: 600, textDecoration: 'none' }}>Edit</a></td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </Shell>
  )
}
