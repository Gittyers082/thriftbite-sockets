import { useCallback } from 'react'
import { api } from '../api/client'
import thryxFull from '../assets/thryx-full.png'
import { Loading, ProblemPanel } from '../components/States'
import { peso, spacedCode, timeOf } from '../lib/format'
import { href, paths } from '../lib/route'
import { gap } from '../lib/style'
import { useLoad } from '../lib/useLoad'

/** The screen the buyer shows at the counter: big code, what to pay. */
export function Pickup({ reservationId }: { reservationId: string }) {
  const loaded = useLoad(useCallback(() => api.myReservations(), []))
  if (loaded.error && !loaded.data) return <ProblemPanel error={loaded.error} onRetry={loaded.reload} />
  if (!loaded.data) return <Loading />

  const r = loaded.data.find((x) => x.id === reservationId)
  if (!r) {
    return (
      <div className="stack">
        <p>This reservation isn’t in your account.</p>
        <a className="btn" href={href(paths.reservations)} style={{ alignSelf: 'flex-start' }}>Your reservations</a>
      </div>
    )
  }

  return (
    <div className="stack" style={gap(0, { maxWidth: 420, margin: '0 auto', alignItems: 'stretch' })}>
      <a href={href(paths.reservations)} className="small" style={{ fontWeight: 600, marginBottom: 12 }}>Back to reservations</a>
      <img src={thryxFull} alt="" width={150} height={147} style={{ width: 150, alignSelf: 'center', marginBottom: -26, position: 'relative', zIndex: 1 }} />
      <div className="card">
        <div className="stack" style={gap(8, { background: r.status === 'RESERVED' ? 'var(--lime)' : 'var(--glow)', borderBottom: '2px dashed var(--ink)', padding: '28px 16px 20px', alignItems: 'center' })}>
          <span style={{ fontWeight: 700 }}>{r.status === 'RESERVED' ? 'Show this at the counter' : 'This reservation is closed'}</span>
          <span className="wide num ticket-code" aria-label={`Claim code ${r.claimCode?.split('').join(' ')}`}>
            {r.claimCode && spacedCode(r.claimCode)}
          </span>
          {r.status === 'RESERVED' && <span className="tag orange">Due by {timeOf(r.pickupDeadline)}</span>}
        </div>
        <div className="stack" style={gap(10, { padding: '18px 20px' })}>
          <h1 className="wide" style={{ fontSize: 20, lineHeight: '24px' }}>{r.storeName}</h1>
          <p className="muted small">{r.items[0]?.pickupLocation}</p>
          {r.items.map((line) => (
            <p key={line.itemId} className="spread num"><span>{line.quantity} × {line.name}</span><span>{peso(line.unitPrice * line.quantity)}</span></p>
          ))}
          <p className="spread num" style={{ borderTop: '2px solid var(--ink)', paddingTop: 10, fontWeight: 800 }}>
            <span>Pay at the counter</span><span>{peso(r.total)}</span>
          </p>
        </div>
      </div>
      <p className="small muted" style={{ marginTop: 16 }}>Cash or GCash. If you can’t make it, cancel so someone else can have it.</p>
    </div>
  )
}
