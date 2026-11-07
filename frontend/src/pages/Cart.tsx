import { useCallback, useState } from 'react'
import { ApiError, api } from '../api/client'
import type { CartLine, CartResponse, ReservationResponse } from '../api/types'
import { Empty, Loading, ProblemPanel } from '../components/States'
import { messageOf } from '../lib/errors'
import { peso, plural, spacedCode, timeOf } from '../lib/format'
import { href, paths } from '../lib/route'
import { gap } from '../lib/style'
import { useLoad } from '../lib/useLoad'

export function Cart() {
  const loaded = useLoad(useCallback(() => api.cart(), []))
  const [cart, setCart] = useState<CartResponse | null>(null)
  const [busy, setBusy] = useState(false)
  const [conflict, setConflict] = useState<string | null>(null)
  const [problem, setProblem] = useState<string | null>(null)
  const [reserved, setReserved] = useState<ReservationResponse[] | null>(null)
  const current = cart ?? loaded.data

  const change = async (line: CartLine, quantity: number) => {
    setBusy(true)
    setProblem(null)
    try {
      setCart(await api.setCartQuantity(line.itemId, quantity))
    } catch (error) {
      setProblem(messageOf(error))
    } finally {
      setBusy(false)
    }
  }

  const reserve = async () => {
    setBusy(true)
    setConflict(null)
    setProblem(null)
    try {
      setReserved(await api.checkout())
      setCart({ lines: [], total: 0 })
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        // Something sold out (or the reservation limit was hit). Nothing was reserved; show the cart as it is now.
        setConflict(error.message)
        try { setCart(await api.cart()) } catch { /* keep the old cart on screen */ }
      } else {
        setProblem(messageOf(error))
      }
    } finally {
      setBusy(false)
    }
  }

  if (reserved) {
    return (
      <div className="stack" style={gap(24)}>
        <div className="page-head"><h1 className="wide">Reserved</h1></div>
        {reserved.map((r) => (
          <section key={r.id} role="status" className="card notice good" style={{ flexWrap: 'wrap' }}>
            <div className="grow">
              <strong style={{ fontSize: 18 }}>Pick up at {r.storeName} by {timeOf(r.pickupDeadline)}.</strong>
              <p>Show code <strong className="num">{r.claimCode && spacedCode(r.claimCode)}</strong> at the counter and pay {peso(r.total)}.</p>
            </div>
            <a className="btn" href={href(paths.pickup(r.id))}>Show claim code</a>
          </section>
        ))}
        <a className="btn" href={href(paths.reservations)} style={{ alignSelf: 'flex-start' }}>See all reservations</a>
      </div>
    )
  }

  if (loaded.error && !current) return <ProblemPanel error={loaded.error} onRetry={loaded.reload} />
  if (!current) return <Loading label="Loading your cart…" />

  const stores = [...new Map(current.lines.map((l) => [l.storeId, l])).values()]
  const blocked = current.lines.some((l) => !l.available)
  const live = current.lines.filter((l) => l.available)
  const units = live.reduce((sum, l) => sum + l.quantity, 0)
  const pickups = new Set(live.map((l) => l.storeId)).size

  if (current.lines.length === 0) {
    return (
      <Empty title="Your cart is empty" action={<a className="btn teal" href={href(paths.browse)}>See tonight’s deals</a>}>
        Add food from any store, then reserve it all at once.
      </Empty>
    )
  }

  return (
    <div className="stack" style={gap(28)}>
      <div className="page-head">
        <div className="stack" style={gap(8)}>
          <h1 className="wide">Cart</h1>
          <p className="muted">{plural(current.lines.length, 'item')} from {plural(stores.length, 'store')}. Nothing is held for you until you reserve.</p>
        </div>
      </div>

      {conflict && (
        <div role="alert" className="card notice bad">
          <div>
            <strong style={{ fontSize: 18 }}>{conflict}</strong>
            <p>Nothing was reserved, so you haven’t lost anything. Change your cart and try again.</p>
          </div>
        </div>
      )}
      {problem && <p role="alert" className="err">{problem}</p>}

      <div className="cart-layout">
        <div className="stack" style={gap(24)}>
          {stores.map((store) => (
            <section key={store.storeId} className="card" aria-label={store.storeName}>
              <div className="card-head">
                <h2 style={{ fontSize: 17, fontWeight: 700 }}>{store.storeName}</h2>
                <span className="muted small">{store.pickupLocation}</span>
              </div>
              {current.lines.filter((l) => l.storeId === store.storeId).map((line) => (
                <div key={line.itemId} className="card-row stack" style={gap(10, { background: line.available ? undefined : 'var(--glow)' })}>
                  <div className="spread">
                    <strong style={{ color: line.available ? undefined : 'var(--ink-2)' }}>{line.name}</strong>
                    <strong className="num">{line.available ? peso(line.lineTotal) : '–'}</strong>
                  </div>
                  {line.available ? (
                    <div className="row">
                      <button type="button" className="step" aria-label={`One less ${line.name}`} disabled={busy || line.quantity <= 1}
                        onClick={() => change(line, line.quantity - 1)}>−</button>
                      <span className="num wide" style={{ width: 30, textAlign: 'center', fontSize: 18 }}>{line.quantity}</span>
                      <button type="button" className="step" aria-label={`One more ${line.name}`} disabled={busy || line.quantity >= line.quantityLeft}
                        onClick={() => change(line, line.quantity + 1)}>+</button>
                      <span className="muted tiny">{peso(line.price)} each, {line.quantityLeft} left</span>
                      <span className="grow" />
                      <button type="button" className="link-btn" disabled={busy} onClick={() => change(line, 0)}>Remove</button>
                    </div>
                  ) : (
                    <div className="row">
                      <span className="tag orange">Sold out</span>
                      <span className="small grow">Someone reserved the last one, or it expired.</span>
                      <button type="button" className="btn sm" disabled={busy} onClick={() => change(line, 0)}>Remove</button>
                    </div>
                  )}
                </div>
              ))}
            </section>
          ))}
        </div>

        <aside className="card stack" style={gap(12, { padding: 20, alignSelf: 'start' })}>
          <div className="spread num" style={{ fontWeight: 800, fontSize: 18 }}>
            <span>Pay at pickup</span><span>{peso(current.total)}</span>
          </div>
          <button type="button" className="btn teal big" disabled={busy || blocked} onClick={reserve}>
            {busy ? 'Reserving…' : `Reserve ${plural(pickups, 'pickup')} (${plural(units, 'item')})`}
          </button>
          <p className="hint">
            {blocked ? 'Remove the sold-out item to reserve the rest.'
              : 'Checkout reserves everything or nothing. You can hold 2 reservations at a time.'}
          </p>
          <p className="hint">Pay with cash or GCash at the counter.</p>
        </aside>
      </div>
    </div>
  )
}
