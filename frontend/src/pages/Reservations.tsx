import { useState } from 'react'
import { Shell } from '../components/Shell'
import { Tabs } from '../components/Tabs'
import { activeReservation as r, pastReservations, type PastReservation } from '../data/sample'
import { peso } from '../lib/format'
import { href } from '../lib/route'

const statusPill: Record<PastReservation['status'], { label: string; tone: string }> = {
  COMPLETED: { label: 'Picked up', tone: 'good' },
  CANCELLED: { label: 'Cancelled', tone: 'neutral' },
  EXPIRED: { label: 'Missed pickup', tone: 'warn' },
}

export function Reservations() {
  const [tab, setTab] = useState<'active' | 'past'>('active')
  const total = r.lines.reduce((s, l) => s + l.total, 0)
  const code = `${r.claimCode.slice(0, 3)} ${r.claimCode.slice(3)}`

  return (
    <Shell area="buyer" current="/reservations" counts={{ '/reservations': '1 ready' }}>
      <div className="titles stack" style={{ gap: 4 }}>
        <h1 className="title">Reservations</h1>
        <p className="subline">1 pickup ready at {r.store}, due by {r.pickupBy}, 1 h 55 m from now.</p>
      </div>

      <Tabs label="Reservations" selected={tab} onSelect={setTab}
        items={[{ id: 'active', label: 'Ready for pickup', count: 1 }, { id: 'past', label: 'Past', count: pastReservations.length }]} />

      {tab === 'active' ? (
        <article className="card" aria-labelledby="r1"
          style={{ padding: 24, display: 'grid', gridTemplateColumns: '280px minmax(0, 1fr)', gap: 32, maxWidth: 980 }}>
          <div className="stack" style={{ gap: 6 }}>
            <span style={{ fontSize: 12 }} className="muted">Claim code</span>
            <span className="num" style={{ fontSize: 40, lineHeight: '44px', fontWeight: 700, letterSpacing: 4 }}>{code}</span>
            <span className="small muted">Show it at the counter. The store types it in to hand over your food.</span>
            <a className="btn-ghost md" href={href('/pickup')} style={{ alignSelf: 'flex-start', marginTop: 8 }}>Open on my phone</a>
          </div>
          <div className="stack" style={{ gap: 14, minWidth: 0 }}>
            <div className="card-head" style={{ alignItems: 'flex-start' }}>
              <div>
                <h2 id="r1" style={{ fontSize: 18, fontWeight: 600 }}>{r.store}</h2>
                <p className="small muted">{r.area} · pick up {r.window}</p>
              </div>
              <span className="pill warn">Pick up by {r.pickupBy}</span>
            </div>
            <table className="table">
              <thead><tr><th scope="col">Food</th><th scope="col" className="num">Qty</th><th scope="col" className="num">Total</th></tr></thead>
              <tbody>
                {r.lines.map((l) => (
                  <tr key={l.name}><td>{l.name}</td><td className="num">{l.qty}</td><td className="num">{peso(l.total)}</td></tr>
                ))}
                <tr><td style={{ fontWeight: 600 }}>Pay at the counter</td><td /><td className="num" style={{ fontWeight: 700, fontSize: 16 }}>{peso(total)}</td></tr>
              </tbody>
            </table>
            <div className="card-head" style={{ alignItems: 'center' }}>
              <p className="small muted">You saved {peso(r.usualTotal - total)} off the usual {peso(r.usualTotal)}.</p>
              <button type="button" className="btn-ghost md">Cancel reservation</button>
            </div>
          </div>
        </article>
      ) : (
        <table className="table" style={{ maxWidth: 980 }}>
          <thead>
            <tr>
              <th scope="col">Date</th><th scope="col">Store</th><th scope="col">Food</th>
              <th scope="col" className="num">Total</th><th scope="col">Status</th>
            </tr>
          </thead>
          <tbody>
            {pastReservations.map((p) => (
              <tr key={p.id}>
                <td className="num">{p.date}</td>
                <td style={{ fontWeight: 600 }}>{p.store}</td>
                <td>{p.food}</td>
                <td className="num">{peso(p.total)}</td>
                <td><span className={`pill ${statusPill[p.status].tone}`}>{statusPill[p.status].label}</span></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Shell>
  )
}
