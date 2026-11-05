import { useState } from 'react'
import { Shell } from '../components/Shell'
import { Tabs } from '../components/Tabs'
import { PickupWindows } from '../components/Timeline'
import { areas, cart, dealTotals, deals, NOW, type Kind } from '../data/sample'
import { clock, peso, percentOff, plural, untilLabel } from '../lib/format'
import { href } from '../lib/route'

type TabId = Kind | 'all'

const tabItems: { id: TabId; label: string }[] = [
  { id: 'all', label: 'All' },
  { id: 'bakery', label: 'Bakery' },
  { id: 'meals', label: 'Meals' },
  { id: 'snacks', label: 'Snacks and drinks' },
]

export function Browse() {
  const [tab, setTab] = useState<TabId>('all')
  const [area, setArea] = useState('all')

  // Sold-out deals drop off the list; the API only returns food that is still for sale.
  const rows = deals
    .filter((d) => d.left > 0)
    .filter((d) => tab === 'all' || d.kind === tab)
    .filter((d) => area === 'all' || d.area === area)
    .sort((a, b) => a.pickupEnd - b.pickupEnd)

  const inCart = new Set(cart.map((l) => l.dealId))
  const cartTotal = cart.filter((l) => !l.soldOut).reduce((s, l) => s + l.price * l.qty, 0)
  const cartStores = new Set(cart.map((l) => l.store)).size

  // One window per store, from the deals shown.
  const windows = [...new Map(deals.map((d) => [d.store, { label: d.store.replace(' Carinderia', ''), from: d.pickupStart, to: d.pickupEnd }])).values()]
  const firstClose = [...deals].sort((a, b) => a.pickupEnd - b.pickupEnd)[0]

  return (
    <Shell area="buyer" current="/" counts={{ '/cart': String(cart.length) }}>
      <div className="page-head">
        <div className="titles">
          <h1 className="title">Today's surplus</h1>
          <p className="subline">
            {dealTotals.all} deals from 9 Baguio stores, each at a portion of its usual price. Reserve now, pick up tonight, pay at the counter.
          </p>
        </div>
        <p className="stamp">Updated {clock(NOW)}</p>
      </div>

      <div className="with-rail">
        <section aria-label="Deals" className="stack" style={{ gap: 16, minWidth: 0 }}>
          <Tabs label="Kind of food" selected={tab} onSelect={setTab}
            items={tabItems.map((t) => ({ ...t, count: dealTotals[t.id] }))} />

          <div className="page-head" style={{ alignItems: 'center' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <label htmlFor="area" className="small muted">Area</label>
              <select id="area" className="select" value={area} onChange={(e) => setArea(e.target.value)}>
                <option value="all">All of Baguio</option>
                {areas.map((a) => <option key={a} value={a}>{a}</option>)}
              </select>
            </div>
            <p className="small muted">Showing {rows.length} of {dealTotals[tab]} · pickup closing soonest first</p>
          </div>

          <table className="table">
            <thead>
              <tr>
                <th scope="col">Food</th>
                <th scope="col">Pick up</th>
                <th scope="col" className="num">Left</th>
                <th scope="col" className="num">Price</th>
                <th scope="col"><span className="sr-only">Add to cart</span></th>
              </tr>
            </thead>
            <tbody>
              {rows.map((d) => {
                const closingSoon = d.pickupEnd - NOW < 2
                return (
                  <tr key={d.id}>
                    <td>
                      <div className="food">
                        <div className="thumb" aria-hidden="true">Photo</div>
                        <div className="stack">
                          <span className="name">{d.name}</span>
                          <span className="small muted">{d.store} · {d.area}</span>
                        </div>
                      </div>
                    </td>
                    <td>
                      <div className="stack">
                        <span>{clock(d.pickupStart)}–{clock(d.pickupEnd)}</span>
                        <span className={closingSoon ? 'small soon' : 'small muted'}>closes in {untilLabel(NOW, d.pickupEnd)}</span>
                      </div>
                    </td>
                    <td className="num">{d.left}</td>
                    <td className="num">
                      <div className="stack end">
                        <span className="price">{peso(d.price)}</span>
                        <span className="small muted"><s>{peso(d.usual)}</s> <span className="off">{percentOff(d.price, d.usual)}% off</span></span>
                      </div>
                    </td>
                    <td className="end" style={{ width: 104 }}>
                      {inCart.has(d.id)
                        ? <span className="small" style={{ color: 'var(--teal)', fontWeight: 600, whiteSpace: 'nowrap' }}>In your cart</span>
                        : <button type="button" className="btn-ghost" aria-label={`Add ${d.name} to cart`}>Add</button>}
                    </td>
                  </tr>
                )
              })}
              {rows.length === 0 && (
                <tr><td colSpan={5} className="muted">Nothing here right now. Try another area.</td></tr>
              )}
            </tbody>
          </table>
          <button type="button" className="btn-ghost" style={{ alignSelf: 'flex-start' }}>Show more</button>
        </section>

        <aside className="stack" style={{ gap: 16 }}>
          <section className="card" aria-labelledby="cart-h">
            <div className="card-head">
              <h2 id="cart-h" className="card-title">Your cart</h2>
              <span className="small muted">{plural(cart.length, 'item')} · {plural(cartStores, 'store')}</span>
            </div>
            <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column', gap: 6 }}>
              {cart.map((l) => (
                <li key={l.dealId} style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}>
                  <span className={l.soldOut ? 'muted' : undefined}>{l.qty} × {l.name}</span>
                  {l.soldOut
                    ? <span className="small soon">Sold out</span>
                    : <span className="num">{peso(l.price * l.qty)}</span>}
                </li>
              ))}
            </ul>
            <div className="card-head" style={{ borderTop: '1px solid var(--rule)', paddingTop: 12 }}>
              <span className="muted">Pay at pickup</span>
              <span className="num" style={{ fontSize: 20, fontWeight: 700 }}>{peso(cartTotal)}</span>
            </div>
            <a className="btn-primary" href={href('/cart')}>Review and reserve</a>
          </section>

          <section className="card" aria-labelledby="win-h">
            <div className="stack" style={{ gap: 2 }}>
              <h2 id="win-h" className="card-title">Pickup windows tonight</h2>
              <p className="small muted">{firstClose.store} closes first, at {clock(firstClose.pickupEnd)}.</p>
            </div>
            <PickupWindows rows={windows} now={NOW} start={15} end={21} />
          </section>
        </aside>
      </div>
    </Shell>
  )
}
