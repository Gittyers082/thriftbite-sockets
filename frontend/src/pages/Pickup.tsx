import thryxFull from '../assets/thryx-full.png'
import { Icon } from '../components/Icon'
import { activeReservation as r } from '../data/sample'
import { peso } from '../lib/format'
import { href } from '../lib/route'

/** Full-screen claim code for showing at the counter, sized for a phone. */
export function Pickup() {
  const total = r.lines.reduce((s, l) => s + l.total, 0)
  const code = `${r.claimCode.slice(0, 3)} ${r.claimCode.slice(3)}`
  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', maxWidth: 480, margin: '0 auto' }}>
      <div style={{ height: 48, display: 'flex', alignItems: 'center', gap: 8, padding: '0 12px' }}>
        <a href={href('/reservations')} aria-label="Back to reservations"
          style={{ width: 44, height: 44, display: 'inline-flex', alignItems: 'center', justifyContent: 'center', color: 'var(--ink)' }}>
          <Icon name="back" size={20} />
        </a>
        <span style={{ fontSize: 16, fontWeight: 600 }}>Claim code</span>
      </div>

      <main className="sheet" style={{ flex: 1, margin: '0 8px 8px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
          <img src={thryxFull} alt="Thryx, the ThriftBite mascot, holding a burger" width={88} style={{ height: 'auto' }} />
          <div>
            <h1 style={{ fontSize: 20, lineHeight: '26px', fontWeight: 700 }}>{r.store}</h1>
            <p className="small muted">{r.area}, Baguio</p>
          </div>
        </div>

        <div className="card" style={{ alignItems: 'center', gap: 8, padding: '24px 16px' }}>
          <span className="small muted">Show this at the counter</span>
          <span className="num" style={{ fontSize: 44, lineHeight: '48px', fontWeight: 700, letterSpacing: 5 }}>{code}</span>
          <span className="pill warn">Pick up by {r.pickupBy}</span>
        </div>

        <section aria-labelledby="order-h" className="stack" style={{ gap: 8 }}>
          <h2 id="order-h" style={{ fontSize: 14, fontWeight: 600 }}>Your order</h2>
          {r.lines.map((l) => (
            <p key={l.name} style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}>
              <span>{l.qty} × {l.name}</span><span className="num">{peso(l.total)}</span>
            </p>
          ))}
          <p style={{ display: 'flex', justifyContent: 'space-between', gap: 12, borderTop: '1px solid var(--rule)', paddingTop: 10, fontWeight: 700 }}>
            <span>Pay at the counter</span><span className="num">{peso(total)}</span>
          </p>
        </section>

        <p className="small muted" style={{ marginTop: 'auto' }}>Cash or GCash. If you can't make it, cancel so someone else can have it.</p>
      </main>
    </div>
  )
}
