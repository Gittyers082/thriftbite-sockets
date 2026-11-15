import { photoUrl } from '../api/client'
import type { ItemResponse } from '../api/types'
import thryxHead from '../assets/thryx-head.png'
import { peso, pickupLabel, untilLabel } from '../lib/format'
import { href, paths } from '../lib/route'
import { gap } from '../lib/style'

interface Props {
  item: ItemResponse
  now: number
  /** How many of this the buyer already has in their cart. */
  inCart: number | undefined
  /** False for store and admin accounts: they see the card without a button. */
  canReserve: boolean
  loggedIn: boolean
  adding: boolean
  onAdd: () => void
  /** How many live changes this listing has had; a new number replays the flash on its count. */
  flashes?: number
  /** Leave the store's name off, on the store's own page. */
  hideStore?: boolean
}

/** One listing, as buyers see it on the browse page and on a store's page. */
export function DealCard({ item, now, inCart, canReserve, loggedIn, adding, onAdd, flashes = 0, hideStore = false }: Props) {
  const closingSoon = new Date(item.pickupEnd).getTime() - now < 2 * 3600_000
  // A live update can take a listing off sale while it's on screen. It stays put
  // (nothing jumps under a finger about to tap) and goes at the next reload.
  const soldOut = item.status === 'SOLD_OUT' || (item.status === 'AVAILABLE' && item.quantity === 0)
  const gone = soldOut || item.status !== 'AVAILABLE'

  return (
    <article className={`card deal${gone ? ' gone' : ''}`}>
      <div className="photo shot">
        {/* The name is right below, so the picture adds nothing for a screen reader. */}
        {item.photoId
          ? <img src={photoUrl(item.photoId, 'thumb')} alt="" loading="lazy" decoding="async" />
          : <img className="none" src={thryxHead} alt="" />}
        <span className="tag lime tilt">{item.discountPercent}% off</span>
      </div>
      <div className="deal-body">
        <div>
          <h3>{item.name}</h3>
          {!hideStore && <p className="small"><a className="muted" href={href(paths.store(item.storeSlug))}>{item.storeName}</a></p>}
          {hideStore && item.description && <p className="muted small">{item.description}</p>}
        </div>
        <p className="price">
          <strong className="wide num">{peso(item.price)}</strong>
          <s className="muted num small">{peso(item.originalPrice)}</s>
        </p>
        <div className="wrap-row" style={gap(6)}>
          {!gone && <span className={`tag ${closingSoon ? 'orange' : 'cream'}`}>Closes in {untilLabel(item.pickupEnd, now)}</span>}
          <span key={flashes} className={`tag${gone ? ' orange' : ''}${flashes ? ' flash' : ''}`}>
            {soldOut ? 'Sold out' : gone ? 'No longer on sale' : `${item.quantity} left`}
          </span>
        </div>
        <p className="muted small">Pick up {pickupLabel(item.pickupStart, item.pickupEnd, now)}</p>
        {!canReserve ? null : gone ? (
          <button type="button" className="btn sm" disabled>{soldOut ? 'Sold out' : 'Not on sale'}</button>
        ) : inCart ? (
          <a className="btn teal sm" href={href(paths.cart)}>In your cart ({inCart})</a>
        ) : (
          <button type="button" className="btn sm" onClick={onAdd} disabled={adding} aria-label={`Add ${item.name} to cart`}>
            {adding ? 'Adding…' : loggedIn ? 'Add to cart' : 'Log in to reserve'}
          </button>
        )}
      </div>
    </article>
  )
}
