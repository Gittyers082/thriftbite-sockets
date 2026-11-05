import { useState } from 'react'
import thryxFull from '../assets/thryx-full.png'
import { Shell } from '../components/Shell'
import { cart as sampleCart, type CartLine } from '../data/sample'
import { peso, plural } from '../lib/format'
import { href } from '../lib/route'

export function Cart() {
  const [lines, setLines] = useState<CartLine[]>(sampleCart)

  const setQty = (id: string, delta: number) =>
    setLines((ls) => ls.map((l) => (l.dealId === id ? { ...l, qty: Math.min(l.left, Math.max(1, l.qty + delta)) } : l)))
  const remove = (id: string) => setLines((ls) => ls.filter((l) => l.dealId !== id))

  // Every total is summed from the lines as shown.
  const live = lines.filter((l) => !l.soldOut)
  const total = live.reduce((s, l) => s + l.price * l.qty, 0)
  const usual = live.reduce((s, l) => s + l.usual * l.qty, 0)
  const stores = [...new Set(lines.map((l) => l.store))]
  const pickups = new Set(live.map((l) => l.store)).size
  const units = live.reduce((s, l) => s + l.qty, 0)
  const blocked = lines.some((l) => l.soldOut)

  return (
    <Shell area="buyer" current="/cart" counts={{ '/cart': String(lines.length) }}>
      <div className="page-head">
        <div className="titles">
          <h1 className="title">Cart</h1>
          <p className="subline">
            {lines.length === 0
              ? 'Nothing in your cart yet.'
              : `${plural(lines.length, 'item')} from ${plural(stores.length, 'store')}. Nothing is held for you until you reserve.`}
          </p>
        </div>
        <p className="stamp">Updated 5:05 PM</p>
      </div>

      <div className="with-rail">
        <div className="stack" style={{ gap: 28, minWidth: 0 }}>
          {stores.map((store) => {
            const storeLines = lines.filter((l) => l.store === store)
            const first = storeLines[0]
            return (
              <section key={store} aria-label={store} className="stack" style={{ gap: 8 }}>
                <div className="card-head">
                  <h2 className="card-title">{store}</h2>
                  <p className="small muted">{first.area} · pick up {first.window}</p>
                </div>
                <table className="table">
                  <thead>
                    <tr>
                      <th scope="col">Food</th>
                      <th scope="col" className="num">Each</th>
                      <th scope="col" className="center" style={{ width: 128 }}>Quantity</th>
                      <th scope="col" className="num">Total</th>
                      <th scope="col" style={{ width: 72 }}><span className="sr-only">Remove</span></th>
                    </tr>
                  </thead>
                  <tbody>
                    {storeLines.map((l) => (
                      <tr key={l.dealId}>
                        <td>
                          <div className="food">
                            <div className="thumb sm" aria-hidden="true">Photo</div>
                            <div className="stack" style={{ gap: 4 }}>
                              <span className="name" style={l.soldOut ? { color: 'var(--muted)' } : undefined}>{l.name}</span>
                              {l.soldOut
                                ? <span className="pill warn" style={{ alignSelf: 'flex-start' }}>Sold out since you added it</span>
                                : <span className="small muted">{l.left} left</span>}
                            </div>
                          </div>
                        </td>
                        <td className="num">{peso(l.price)}</td>
                        <td className="center">
                          {l.soldOut ? <span className="muted">—</span> : (
                            <div style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                              <button type="button" className="btn-step" aria-label={`One less ${l.name}`} onClick={() => setQty(l.dealId, -1)}>−</button>
                              <span className="num" aria-live="polite" style={{ minWidth: 24, fontWeight: 600 }}>{l.qty}</span>
                              <button type="button" className="btn-step" aria-label={`One more ${l.name}`} onClick={() => setQty(l.dealId, 1)}>+</button>
                            </div>
                          )}
                        </td>
                        <td className="num" style={{ fontWeight: 600 }}>{l.soldOut ? '—' : peso(l.price * l.qty)}</td>
                        <td className="end"><button type="button" className="btn-text" onClick={() => remove(l.dealId)}>Remove</button></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </section>
            )
          })}

          {lines.length === 0 && (
            <div className="empty">
              <img src={thryxFull} alt="Thryx, the ThriftBite mascot, holding a burger" />
              <div className="stack" style={{ gap: 6 }}>
                <p style={{ fontWeight: 600, fontSize: 15 }}>Your cart is empty.</p>
                <a href={href('/')} style={{ fontWeight: 600 }}>Browse today's surplus</a>
              </div>
            </div>
          )}
        </div>

        <aside className="card" aria-labelledby="sum-h">
          <h2 id="sum-h" className="card-title">Summary</h2>
          <dl style={{ margin: 0, display: 'grid', gridTemplateColumns: '1fr auto', rowGap: 8, columnGap: 16 }}>
            <dt className="muted">Usual price</dt><dd className="num" style={{ margin: 0 }}>{peso(usual)}</dd>
            <dt className="muted">You save</dt><dd className="num off" style={{ margin: 0 }}>{peso(usual - total)}</dd>
          </dl>
          <div className="card-head" style={{ borderTop: '1px solid var(--rule)', paddingTop: 12 }}>
            <span>Pay at pickup</span>
            <span className="num" style={{ fontSize: 28, lineHeight: '34px', fontWeight: 700 }}>{peso(total)}</span>
          </div>
          <p className="small muted">Cash or GCash at the counter. Each store gets its own claim code.</p>
          {blocked && <p role="status" className="notice">Remove the sold-out item to reserve.</p>}
          {blocked || live.length === 0 ? (
            <button type="button" className="btn-primary" disabled>Reserve {plural(pickups, 'pickup')} ({plural(units, 'item')})</button>
          ) : (
            <a className="btn-primary" href={href('/reservations')}>Reserve {plural(pickups, 'pickup')} ({plural(units, 'item')})</a>
          )}
        </aside>
      </div>
    </Shell>
  )
}
