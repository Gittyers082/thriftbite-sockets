import { useCallback, useState, type FormEvent } from 'react'
import { api } from '../api/client'
import thryxFull from '../assets/thryx-full.png'
import { DealCard } from '../components/DealCard'
import { LiveBadge } from '../components/LiveBadge'
import { DealSkeletons, Empty, ProblemPanel } from '../components/States'
import { peso, plural } from '../lib/format'
import { href, paths } from '../lib/route'
import { gap } from '../lib/style'
import { useCart } from '../lib/useCart'
import { useLiveStock } from '../lib/useLiveStock'
import { useLoad, useNow } from '../lib/useLoad'

type Sort = 'closing' | 'discount'

export function Browse() {
  const now = useNow()
  const [typed, setTyped] = useState('')
  const [query, setQuery] = useState('')
  const [sort, setSort] = useState<Sort>('closing')
  const { cart, inCart, canReserve, loggedIn, adding, problem, add } = useCart()

  const deals = useLoad(useCallback(() => api.browse(query), [query]))
  // Live: when someone reserves a listing on screen, its count changes in place.
  const mutateDeals = deals.mutate
  const changes = useLiveStock(deals.data?.content,
    useCallback((change) => mutateDeals((page) => ({ ...page, content: change(page.content) })), [mutateDeals]),
    deals.reload)

  const items = [...(deals.data?.content ?? [])].sort((a, b) =>
    sort === 'discount' ? b.discountPercent - a.discountPercent : a.pickupEnd.localeCompare(b.pickupEnd))

  const search = (e: FormEvent) => {
    e.preventDefault()
    setQuery(typed.trim())
  }

  const cartCount = cart?.lines.length ?? 0
  const stores = new Set(cart?.lines.map((line) => line.storeId)).size

  return (
    <div className="stack" style={gap(28)}>
      <section className="card" style={{ background: 'var(--teal)', color: '#fff', position: 'relative', overflow: 'hidden' }}>
        <div className="stack" style={gap(18, { padding: '40px 40px 44px', maxWidth: 680 })}>
          <h1 className="wide" style={{ fontSize: 'clamp(30px, 5vw, 56px)', lineHeight: 1.02, letterSpacing: -1.5 }}>
            Tonight’s surplus, saved before closing.
          </h1>
          <p style={{ fontSize: 18, lineHeight: '28px', maxWidth: 520 }}>
            End-of-day food from Baguio bakeries and carinderias, at a portion of the usual price.
            Reserve it now and pick it up tonight.
          </p>
          <form role="search" className="row" onSubmit={search} style={{ maxWidth: 560 }}>
            <label htmlFor="q" className="sr">Search food or a store</label>
            <input id="q" className="input grow" type="search" placeholder="Pandesal, adobo, a store…"
              value={typed} onChange={(e) => setTyped(e.target.value)} style={{ boxShadow: '4px 4px 0 var(--ink)' }} />
            <button type="submit" className="btn lime" style={{ boxShadow: '4px 4px 0 var(--ink)' }}>Search</button>
          </form>
        </div>
        <img src={thryxFull} alt="" width={300} height={294} className="hide-phone"
          style={{ position: 'absolute', right: 24, bottom: -36, width: 300, height: 'auto' }} />
      </section>

      <div className="spread" style={{ flexWrap: 'wrap' }}>
        <p className="muted" aria-live="polite">
          {deals.data && (query
            ? `${plural(deals.data.totalElements, 'deal')} for “${query}”`
            : `${plural(deals.data.totalElements, 'deal')} you can still pick up tonight`)}
        </p>
        <div className="row">
          <LiveBadge what="Stock counts" />
          <label htmlFor="sort" style={{ fontWeight: 600 }}>Sort</label>
          <select id="sort" className="input" style={{ width: 200, height: 40 }} value={sort}
            onChange={(e) => setSort(e.target.value as Sort)}>
            <option value="closing">Closing soonest</option>
            <option value="discount">Biggest discount</option>
          </select>
        </div>
      </div>

      {problem && <p role="alert" className="err">{problem}</p>}

      {deals.error ? <ProblemPanel error={deals.error} onRetry={deals.reload} />
        : !deals.data ? <DealSkeletons />
        : items.length === 0 ? (
          query ? (
            <Empty title={`Nothing matches “${query}” right now`}
              action={<button type="button" className="btn" onClick={() => { setTyped(''); setQuery('') }}>Show all deals</button>}>
              Try a shorter word, like “bread” or a store’s name.
            </Empty>
          ) : (
            <Empty title="Tonight’s food is all spoken for."
              action={<a className="btn" href={href(paths.reservations)}>See your reservations</a>}>
              Every pickup window has closed. Stores usually post between 3:00 and 4:00 PM, so check back tomorrow afternoon.
            </Empty>
          )
        ) : (
          <>
            <h2 className="sr">Deals, {sort === 'closing' ? 'pickup closing soonest first' : 'biggest discount first'}</h2>
            <div className="deals">
              {items.map((item) => (
                <DealCard key={item.id} item={item} now={now} inCart={inCart.get(item.id)} canReserve={canReserve}
                  loggedIn={loggedIn} adding={adding === item.id} onAdd={() => add(item)} flashes={changes[item.id]} />
              ))}
            </div>
          </>
        )}

      {cartCount > 0 && (
        <div style={{ position: 'sticky', bottom: 16, zIndex: 5 }} className="cart-bar">
          <div className="card spread" style={{ background: 'var(--ink)', color: '#fff', boxShadow: '5px 5px 0 var(--teal)', padding: '12px 16px 12px 22px' }}>
            <p><strong>{plural(cartCount, 'item')} from {plural(stores, 'store')}.</strong>{' '}
              <span style={{ color: '#CDE3E2' }}>Pay {peso(cart?.total ?? 0)} at pickup.</span></p>
            <a className="btn lime sm" href={href(paths.cart)} style={{ boxShadow: '2px 2px 0 #fff' }}>Review cart</a>
          </div>
        </div>
      )}
    </div>
  )
}
