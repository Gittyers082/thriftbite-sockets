import type { ReactNode } from 'react'
import thryxHead from '../assets/thryx-head.png'
import { href, type Route } from '../lib/route'
import { Icon, type IconName } from './Icon'

export type Area = 'buyer' | 'owner' | 'admin'

interface NavItem {
  label: string
  route: Route
  icon: IconName
  count?: string
}

interface ShellProps {
  area: Area
  current: Route
  /** Counts shown on nav items that are queues needing action. */
  counts?: Partial<Record<Route, string>>
  /** Replaces the centred search in the top bar (e.g. breadcrumbs). */
  topbar?: ReactNode
  children: ReactNode
}

const navByArea: Record<Area, NavItem[]> = {
  buyer: [
    { label: "Today's surplus", route: '/', icon: 'bag' },
    { label: 'Cart', route: '/cart', icon: 'cart' },
    { label: 'Reservations', route: '/reservations', icon: 'ticket' },
  ],
  owner: [
    { label: 'Today', route: '/owner', icon: 'home' },
    { label: 'New listing', route: '/owner/new', icon: 'plus' },
  ],
  admin: [{ label: 'Store approvals', route: '/admin', icon: 'store' }],
}

const people: Record<Area, { name: string; role: string; initial: string; bg: string; ink: string }> = {
  buyer: { name: 'steven', role: 'Buyer', initial: 'S', bg: 'var(--good-bg)', ink: 'var(--good-ink)' },
  owner: { name: 'rosa.sunrise', role: 'Owner', initial: 'R', bg: 'var(--sale-bg)', ink: 'var(--sale-ink)' },
  admin: { name: 'admin', role: 'Administrator', initial: 'A', bg: 'var(--neutral-bg)', ink: 'var(--neutral-ink)' },
}

const subtitle: Record<Area, string> = { buyer: 'Baguio City', owner: 'for stores', admin: 'Admin' }
const searchPlaceholder: Record<Area, string> = {
  buyer: 'Search pandesal, adobo, a store…',
  owner: 'Search your listings or a buyer',
  admin: 'Search store accounts',
}

/** The app frame: a tinted L (sidebar + top bar) around a white sheet. */
export function Shell({ area, current, counts = {}, topbar, children }: ShellProps) {
  const person = people[area]
  return (
    <div className="shell">
      <aside className="sidebar">
        <a className="brand" href={href(navByArea[area][0].route)}>
          <img src={thryxHead} alt="" width={40} height={40} />
          <span>
            <strong>ThriftBite</strong>
            <span>{subtitle[area]}</span>
          </span>
        </a>
        {area === 'owner' && (
          <div className="scope">
            <span>Store</span>
            <span>Sunrise Bakery</span>
          </div>
        )}
        <nav className="nav" aria-label="Main">
          {navByArea[area].map((item) => (
            <a key={item.route} href={href(item.route)} aria-current={item.route === current ? 'page' : undefined}>
              <Icon name={item.icon} />
              {item.label}
              {counts[item.route] && <span className="count">{counts[item.route]}</span>}
            </a>
          ))}
        </nav>
        <div className="user">
          <span className="avatar" aria-hidden="true" style={{ background: person.bg, color: person.ink }}>{person.initial}</span>
          <span>
            <span style={{ fontWeight: 600 }}>{person.name}</span>
            <small>{person.role}</small>
          </span>
        </div>
      </aside>

      {topbar ?? (
        <div className="topbar">
          <form role="search" className="search" onSubmit={(e) => e.preventDefault()}>
            <label htmlFor="q" className="sr-only">Search</label>
            <Icon name="search" size={14} />
            <input id="q" type="search" placeholder={searchPlaceholder[area]} />
          </form>
        </div>
      )}

      <main className="sheet">{children}</main>
    </div>
  )
}
