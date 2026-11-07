import { useCallback, useState } from 'react'
import { api } from '../api/client'
import type { ReservationResponse, ReservationStatus } from '../api/types'
import { Empty, Loading, ProblemPanel } from '../components/States'
import { messageOf } from '../lib/errors'
import { dayOf, peso, spacedCode, timeOf } from '../lib/format'
import { href, paths } from '../lib/route'
import { gap } from '../lib/style'
import { useLoad } from '../lib/useLoad'

const statusLabel: Record<ReservationStatus, [string, string]> = {
  RESERVED: ['Ready for pickup', 'lime'],
  COMPLETED: ['Picked up', 'teal'],
  CANCELLED: ['Cancelled', ''],
  EXPIRED: ['Missed pickup', 'orange'],
}

export function Reservations() {
  const loaded = useLoad(useCallback(() => api.myReservations(), []))
  const [tab, setTab] = useState<'active' | 'past'>('active')
  const [confirming, setConfirming] = useState<string | null>(null)
  const [problem, setProblem] = useState<string | null>(null)

  if (loaded.error && !loaded.data) return <ProblemPanel error={loaded.error} onRetry={loaded.reload} />
  if (!loaded.data) return <Loading label="Loading your reservations…" />

  const active = loaded.data.filter((r) => r.status === 'RESERVED')
  const past = loaded.data.filter((r) => r.status !== 'RESERVED')

  const cancel = async (r: ReservationResponse) => {
    setProblem(null)
    try {
      await api.cancel(r.id)
      setConfirming(null)
      loaded.reload()
    } catch (error) {
      setProblem(messageOf(error))
    }
  }

  return (
    <div className="stack" style={gap(24)}>
      <div className="page-head">
        <h1 className="wide">Reservations</h1>
        <div role="tablist" aria-label="Show" className="segmented">
          <button type="button" role="tab" className="chip" aria-selected={tab === 'active'} onClick={() => setTab('active')}>
            Ready for pickup <span className="n">{active.length}</span>
          </button>
          <button type="button" role="tab" className="chip" aria-selected={tab === 'past'} onClick={() => setTab('past')}>
            Past <span className="n">{past.length}</span>
          </button>
        </div>
      </div>
      {problem && <p role="alert" className="err">{problem}</p>}

      {tab === 'active' && (active.length === 0 ? (
        <Empty title="Nothing to pick up" action={<a className="btn teal" href={href(paths.browse)}>See tonight’s deals</a>}>
          Reserve food and its claim code shows up here.
        </Empty>
      ) : (
        <div className="two" style={{ alignItems: 'start' }}>
          {active.map((r) => (
            <section key={r.id} className="card">
              <div className="card-head" style={{ background: 'var(--lime)' }}>
                <div>
                  <h2 style={{ fontSize: 18, fontWeight: 800 }}>{r.storeName}</h2>
                  <p className="small">{r.items[0]?.pickupLocation}</p>
                </div>
                <span className="tag orange">Due {timeOf(r.pickupDeadline)}</span>
              </div>
              <div className="stack" style={gap(8, { padding: '16px 20px' })}>
                {r.items.map((line) => (
                  <p key={line.itemId} className="spread num"><span>{line.quantity} × {line.name}</span><span>{peso(line.unitPrice * line.quantity)}</span></p>
                ))}
                <p className="spread num" style={{ borderTop: '2px solid var(--ink)', paddingTop: 8, fontWeight: 800 }}>
                  <span>Pay at the counter</span><span>{peso(r.total)}</span>
                </p>
                <p className="small">Claim code <strong className="num">{r.claimCode && spacedCode(r.claimCode)}</strong></p>
                <a className="btn teal big" href={href(paths.pickup(r.id))}>Show claim code</a>
                {confirming === r.id ? (
                  <div className="panel stack" style={gap(10, { padding: 14, borderColor: 'var(--bad)' })}>
                    <strong>Cancel this reservation?</strong>
                    <p className="small">The food goes back on sale for someone else.</p>
                    <div className="row">
                      <button type="button" className="btn sm" onClick={() => setConfirming(null)}>Keep it</button>
                      <button type="button" className="btn sm danger" onClick={() => cancel(r)}>Cancel reservation</button>
                    </div>
                  </div>
                ) : (
                  <button type="button" className="btn" onClick={() => setConfirming(r.id)}>Cancel reservation</button>
                )}
                <p className="hint">Cancelling before the pickup window starts never counts as a missed pickup.</p>
              </div>
            </section>
          ))}
        </div>
      ))}

      {tab === 'past' && (past.length === 0 ? (
        <p className="muted">No past reservations yet.</p>
      ) : (
        <section className="card tbl-scroll">
          <table className="tbl">
            <thead>
              <tr><th scope="col">Date</th><th scope="col">Store</th><th scope="col">Food</th><th scope="col" className="r">Total</th><th scope="col">Status</th></tr>
            </thead>
            <tbody>
              {past.map((r) => (
                <tr key={r.id}>
                  <td className="num">{dayOf(r.createdAt)}</td>
                  <td><strong>{r.storeName}</strong></td>
                  <td className="small">{r.items.map((l) => `${l.quantity} × ${l.name}`).join(', ')}</td>
                  <td className="r num">{peso(r.total)}</td>
                  <td><span className={`tag ${statusLabel[r.status][1]}`}>{statusLabel[r.status][0]}</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      ))}
    </div>
  )
}
