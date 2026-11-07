import { useCallback, useState, type FormEvent } from 'react'
import { api } from '../api/client'
import type { CartResponse, ItemResponse } from '../api/types'
import thryxFull from '../assets/thryx-full.png'
import { DealSkeletons, Empty, ProblemPanel } from '../components/States'
import { messageOf } from '../lib/errors'
import { useAuth } from '../lib/auth-context'
import { peso, plural, untilLabel, windowOf } from '../lib/format'
import { href, navigate, paths } from '../lib/route'
import { gap } from '../lib/style'
import { useLoad, useNow } from '../lib/useLoad'

type Sort = 'closing' | 'discount'

export function Browse() {
  const { user } = useAuth()
  const isBuyer = user?.role === 'CONSUMER'
  const now = useNow()
  const [typed, setTyped] = useState('')
  const [query, setQuery] = useState('')
  const [sort, setSort] = useState<Sort>('closing')
  const [cart, setCart] = useState<CartResponse | null>(null)
  const [adding, setAdding] = useState<string | null>(null)
  const [problem, setProblem] = useState<string | null>(null)

  const deals = useLoad(useCallback(() => api.browse(query), [query]))
  const loadedCart = useLoad(useCallback(() => (isBuyer ? api.cart() : Promise.resolve(null)), [isBuyer]))
  const currentCart = cart ?? loadedCart.data ?? null
  const inCart = new Map(currentCart?.lines.map((line) => [line.itemId, line.quantity]) ?? [])

  const items = [...(deals.data?.content ?? [])].sort((a, b) =>
    sort === 'discount' ? b.discountPercent - a.discountPercent : a.pickupEnd.localeCompare(b.pickupEnd))

  const search = (e: FormEvent) => {
    e.preventDefault()
    setQuery(typed.trim())
  }

  const add = async (item: ItemResponse) => {
    if (!user) return navigate(paths.login)
    setAdding(item.id)
    setProblem(null)
    try {
      setCart(await api.setCartQuantity(item.id, (inCart.get(item.id) ?? 0) + 1))
    } catch (error) {
      setProblem(messageOf(error))
    } finally {
      setAdding(null)
    }
  }

  const cartCount = currentCart?.lines.length ?? 0
  const stores = new Set(currentCart?.lines.map((line) => line.storeId)).size

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
              {items.map((item) => {
                const qty = inCart.get(item.id)
                const closingSoon = new Date(item.pickupEnd).getTime() - now < 2 * 3600_000
                return (
                  <article key={item.id} className="card deal">
                    <div className="photo">
                      <span>Photo</span>
                      <span className="tag lime tilt">{item.discountPercent}% off</span>
                    </div>
                    <div className="deal-body">
                      <div>
                        <h3>{item.name}</h3>
                        <p className="muted small">{item.storeName}</p>
                      </div>
                      <p className="price">
                        <strong className="wide num">{peso(item.price)}</strong>
                        <s className="muted num small">{peso(item.originalPrice)}</s>
                      </p>
                      <div className="wrap-row" style={gap(6)}>
                        <span className={`tag ${closingSoon ? 'orange' : 'cream'}`}>Closes in {untilLabel(item.pickupEnd, now)}</span>
                        <span className="tag">{item.quantity} left</span>
                      </div>
                      <p className="muted small">Pick up {windowOf(item.pickupStart, item.pickupEnd)}</p>
                      {user && !isBuyer ? null : qty ? (
                        <a className="btn teal sm" href={href(paths.cart)}>In your cart ({qty})</a>
                      ) : (
                        <button type="button" className="btn sm" onClick={() => add(item)}
                          disabled={adding === item.id} aria-label={`Add ${item.name} to cart`}>
                          {adding === item.id ? 'Adding…' : user ? 'Add to cart' : 'Log in to reserve'}
                        </button>
                      )}
                    </div>
                  </article>
                )
              })}
            </div>
          </>
        )}

      {cartCount > 0 && (
        <div style={{ position: 'sticky', bottom: 16, zIndex: 5 }} className="cart-bar">
          <div className="card spread" style={{ background: 'var(--ink)', color: '#fff', boxShadow: '5px 5px 0 var(--teal)', padding: '12px 16px 12px 22px' }}>
            <p><strong>{plural(cartCount, 'item')} from {plural(stores, 'store')}.</strong>{' '}
              <span style={{ color: '#CDE3E2' }}>Pay {peso(currentCart?.total ?? 0)} at pickup.</span></p>
            <a className="btn lime sm" href={href(paths.cart)} style={{ boxShadow: '2px 2px 0 #fff' }}>Review cart</a>
          </div>
        </div>
      )}
    </div>
  )
}
