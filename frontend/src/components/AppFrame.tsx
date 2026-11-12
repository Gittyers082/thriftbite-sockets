import type { ReactNode } from 'react'
import thryxHead from '../assets/thryx-head.png'
import { homeFor, useAuth } from '../lib/auth-context'
import { href, paths, useRoute } from '../lib/route'
import { Icon, type IconName } from './Icon'

interface NavItem {
  label: string
  path: string
  icon: IconName
  /** A shorter name for the phone's tab bar. */
  short?: string
  /** Left off the phone's tab bar (five tabs is the most that fits); reached from another screen instead. */
  desktopOnly?: boolean
}

function navFor(role: string | undefined): NavItem[] {
  if (role === 'OWNER') {
    return [
      { label: 'Today', path: paths.owner, icon: 'today' },
      { label: 'Sales', path: paths.sales, icon: 'sales' },
      { label: 'New listing', short: 'New', path: paths.newListing, icon: 'plus' },
      { label: 'Post again', path: paths.postAgain, icon: 'plus', desktopOnly: true },
      { label: 'Store profile', short: 'Store', path: paths.storeProfile, icon: 'store' },
    ]
  }
  if (role === 'ADMIN') {
    return [{ label: 'Store approvals', path: paths.admin, icon: 'store' }]
  }
  return [
    { label: 'Browse', path: paths.browse, icon: 'browse' },
    { label: 'Cart', path: paths.cart, icon: 'cart' },
    { label: 'Reservations', path: paths.reservations, icon: 'ticket' },
  ]
}

const avatarColor: Record<string, string> = { OWNER: 'var(--orange)', ADMIN: 'var(--ink)', CONSUMER: 'var(--lime)' }

/** Header with the nav for the signed-in role; on phones the nav moves to a bottom tab bar. */
export function AppFrame({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const path = useRoute()
  const nav = navFor(user?.role)
  // The longest matching path wins, so "Today" (/owner) isn't lit on /owner/sales.
  const current = nav.filter((item) => item.path === path || (item.path !== '/' && path.startsWith(item.path + '/')))
    .sort((a, b) => b.path.length - a.path.length)[0]?.path ?? path
  const isCurrent = (item: NavItem) => item.path === current
  const ownerArea = user?.role === 'OWNER' || user?.role === 'ADMIN'
  const meTab: NavItem = { label: user ? 'Account' : 'Log in', path: user ? paths.account : paths.login, icon: 'person' }

  return (
    <div className="page">
      <a className="sr" href="#main" onClick={(e) => { e.preventDefault(); document.getElementById('main')?.focus() }}>
        Skip to content
      </a>
      <header className="bar">
        <div className="wrap">
          <a className="mark" href={href(homeFor(user))}>
            <img src={thryxHead} alt="" width={40} height={40} />
            <span>ThriftBite</span>
          </a>
          <nav className="links" aria-label={ownerArea ? 'Store' : 'Main'}>
            {nav.filter((item) => ownerArea || item.path !== paths.cart).map((item) => (
              <a key={item.path} href={href(item.path)} aria-current={isCurrent(item) ? 'page' : undefined}>
                {item.label}
              </a>
            ))}
          </nav>
          <span className="grow hide-desktop" />
          {!ownerArea && (
            <a className="btn sm hide-phone" href={href(paths.cart)} aria-current={path === paths.cart ? 'page' : undefined}>
              <Icon name="cart" size={18} /> Cart
            </a>
          )}
          {user ? (
            <a className="avatar" href={href(paths.account)} aria-label={`Account: ${user.username}`}
              style={{ background: avatarColor[user.role], color: user.role === 'ADMIN' ? '#fff' : undefined }}>
              {user.username.charAt(0)}
            </a>
          ) : (
            <a className="btn sm" href={href(paths.login)}>Log in</a>
          )}
        </div>
      </header>

      <main id="main" tabIndex={-1} className="wrap main grow" style={{ outline: 'none' }}>
        {children}
      </main>

      <nav className="tabbar" aria-label={ownerArea ? 'Store' : 'Main'}>
        {[...nav.filter((item) => !item.desktopOnly), meTab].map((item) => (
          <a key={item.path} href={href(item.path)} aria-current={isCurrent(item) ? 'page' : undefined}>
            <Icon name={item.icon} />
            <span>{item.short ?? item.label}</span>
          </a>
        ))}
      </nav>
    </div>
  )
}
