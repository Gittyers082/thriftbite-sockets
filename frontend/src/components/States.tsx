import type { ReactNode } from 'react'
import thryxFull from '../assets/thryx-full.png'
import { ApiError, NetworkError } from '../api/client'
import { messageOf } from '../lib/errors'
import { href, paths } from '../lib/route'

/** For a failed load: offline, server error, or the API's own message, with a retry. */
export function ProblemPanel({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const offline = error instanceof NetworkError
  const server = error instanceof ApiError && error.status >= 500
  return (
    <div role="alert" className="panel notice" style={{ background: offline ? 'var(--ink)' : 'var(--paper)', color: offline ? '#fff' : undefined }}>
      <span className="tag orange" style={{ flex: 'none' }}>{offline ? 'Offline' : server ? 'Error' : 'Problem'}</span>
      <p className="grow">
        {offline
          ? "Can't reach ThriftBite. If the site was asleep, it can take up to a minute to wake up."
          : messageOf(error)}
      </p>
      {onRetry && <button type="button" className="btn sm lime" onClick={onRetry}>Try again</button>}
    </div>
  )
}

/** An empty screen is an invitation to act. */
export function Empty({ title, children, action }: { title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <section className="card cream empty">
      <img src={thryxFull} alt="" width={220} height={216} className="hide-phone" />
      <div className="stack" style={{ maxWidth: 560 }}>
        <h2 className="wide" style={{ fontSize: 30, lineHeight: '34px' }}>{title}</h2>
        {children && <div style={{ fontSize: 17, lineHeight: '26px' }}>{children}</div>}
        {action && <div className="wrap-row">{action}</div>}
      </div>
    </section>
  )
}

/** Placeholder cards in the shape of the deal grid, so nothing jumps when deals arrive. */
export function DealSkeletons({ count = 8 }: { count?: number }) {
  return (
    <div className="deals" aria-hidden="true">
      {Array.from({ length: count }, (_, i) => (
        <div key={i} className="card deal">
          <div className="sk photo" />
          <div className="deal-body">
            <div className="sk" style={{ height: 18, width: `${65 + ((i * 13) % 30)}%` }} />
            <div className="sk" style={{ height: 14, width: '55%' }} />
            <div className="sk" style={{ height: 26, width: '40%' }} />
            <div className="sk" style={{ height: 44, marginTop: 'auto' }} />
          </div>
        </div>
      ))}
    </div>
  )
}

export function Loading({ label = 'Loading…' }: { label?: string }) {
  return <p role="status" className="muted" style={{ fontWeight: 600 }}>{label}</p>
}

/** Shown when a page needs a login, or a different kind of account. */
export function NeedsAccount({ what }: { what: 'login' | 'store' | 'buyer' | 'admin' }) {
  const text = {
    login: ['Log in to see this', 'You need an account for this page.'],
    store: ['This page is for stores', 'Log in with a store account to see it.'],
    buyer: ['This page is for buyers', 'Store and admin accounts can’t reserve food.'],
    admin: ['This page is for admins', 'Log in with an admin account to see it.'],
  }[what]
  return (
    <Empty title={text[0]} action={<a className="btn teal" href={href(paths.login)}>Log in</a>}>
      {text[1]}
    </Empty>
  )
}
