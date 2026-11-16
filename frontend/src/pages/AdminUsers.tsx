import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { api } from '../api/client'
import type { AdminUser } from '../api/types'
import { Empty, Loading, ProblemPanel } from '../components/States'
import { useAuth } from '../lib/auth-context'
import { messageOf } from '../lib/errors'
import { dayOf, plural } from '../lib/format'
import { href, paths } from '../lib/route'
import { gap } from '../lib/style'
import { useLoad } from '../lib/useLoad'

type Show = '' | 'buyers' | 'stores' | 'suspended'
const filters: { show: Show; label: string }[] = [
  { show: '', label: 'Everyone' },
  { show: 'buyers', label: 'Buyers' },
  { show: 'stores', label: 'Stores' },
  { show: 'suspended', label: 'Suspended' },
]

/** Buyers and stores. An admin can suspend one, with a reason, and lift it again. */
export function AdminUsers() {
  const { user } = useAuth()
  const isDemo = user?.username === 'demo.admin'
  const [typed, setTyped] = useState('')
  const [search, setSearch] = useState('')
  const [show, setShow] = useState<Show>('')
  const loaded = useLoad(useCallback(() => api.adminUsers(search, show), [search, show]))

  // Search a moment after the typing stops, not on every letter.
  useEffect(() => {
    const timer = window.setTimeout(() => setSearch(typed.trim()), 300)
    return () => window.clearTimeout(timer)
  }, [typed])

  return (
    <div className="stack" style={gap(24, { maxWidth: 820 })}>
      <div className="page-head">
        <div className="stack" style={gap(6)}>
          <h1 className="wide">Users</h1>
          <p className="muted">Buyers and stores. Suspended accounts are listed first.</p>
        </div>
      </div>
      {isDemo && (
        <p className="panel notice dark">
          You’re the demo admin, so you only see the demo’s sample accounts, and you can’t suspend the ones every visitor logs in with.
        </p>
      )}
      <div className="wrap-row" style={{ ['--gap' as string]: '12px', alignItems: 'flex-end' }}>
        <div className="field grow" style={{ minWidth: 220 }}>
          <label htmlFor="user-search">Search</label>
          <input id="user-search" className="input" type="search" placeholder="Username or store name" value={typed}
            onChange={(e) => setTyped(e.target.value)} />
        </div>
        <div role="tablist" aria-label="Show" className="segmented">
          {filters.map((f) => (
            <button key={f.show} type="button" role="tab" className="chip" aria-selected={show === f.show} onClick={() => setShow(f.show)}>{f.label}</button>
          ))}
        </div>
      </div>

      {loaded.error && !loaded.data ? <ProblemPanel error={loaded.error} onRetry={loaded.reload} />
        : !loaded.data ? <Loading label="Loading accounts…" />
        : loaded.data.length === 0 ? (
          <Empty title={show === 'suspended' && !search ? 'Nobody is suspended' : 'No accounts match'}>
            {show === 'suspended' && !search ? 'Suspended accounts show up here.' : 'Try a shorter search, or a different filter.'}
          </Empty>
        ) : (
          <section className="card" aria-label="Accounts">
            {loaded.data.map((account, i) => <UserRow key={account.id} account={account} first={i === 0} onChanged={loaded.reload} />)}
            {loaded.data.length === 100 && <p className="hint" style={{ padding: '12px 20px' }}>Showing the first 100. Search to narrow it down.</p>}
          </section>
        )}
    </div>
  )
}

function UserRow({ account, first, onChanged }: { account: AdminUser; first: boolean; onChanged: () => void }) {
  const [asking, setAsking] = useState(false)
  const [reason, setReason] = useState('')
  const [touched, setTouched] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const isStore = account.role === 'OWNER'
  const tooShort = reason.trim().length < 10
  const id = `reason-${account.id}`

  const run = async (action: () => Promise<unknown>) => {
    setError(null)
    setBusy(true)
    try {
      await action()
      setAsking(false)
      setReason('')
      setTouched(false)
      onChanged()
    } catch (err) {
      setError(messageOf(err))
    } finally {
      setBusy(false)
    }
  }
  const suspend = (e: FormEvent) => {
    e.preventDefault()
    setTouched(true)
    if (!tooShort) void run(() => api.suspendUser(account.id, reason.trim()))
  }

  const signals = isStore
    ? [account.reportsAbout > 0 && `${plural(account.reportsAbout, 'report')} about it in 90 days`]
    : [account.missedPickups > 0 && `${plural(account.missedPickups, 'missed pickup')} in 30 days`,
       account.reportsSent > 0 && `sent ${plural(account.reportsSent, 'report')} in 90 days`]

  return (
    <div className="card-row stack" style={gap(10, { borderTop: first ? 0 : undefined, padding: '14px 20px' })}>
      <div className="spread" style={{ flexWrap: 'wrap', gap: 12 }}>
        <div className="row" style={gap(12, { minWidth: 0 })}>
          <span className="avatar" aria-hidden="true" style={{ background: isStore ? 'var(--orange)' : 'var(--lime)' }}>{account.username.charAt(0)}</span>
          <div style={{ minWidth: 0 }}>
            <strong>{account.username}</strong>
            <p className="hint">
              {isStore
                ? account.storeSlug && account.approved
                  ? <>Store: <a href={href(paths.store(account.storeSlug))}>{account.storeName}</a></>
                  : <>Store: {account.storeName}{!account.approved && ' (waiting for approval)'}</>
                : 'Buyer'}
              , joined {dayOf(account.createdAt)}
            </p>
          </div>
        </div>
        <div className="row" style={gap(8)}>
          {account.suspendedAt && <span className="tag orange">Suspended</span>}
          {account.suspendedAt
            ? <button type="button" className="btn sm" disabled={busy} onClick={() => run(() => api.unsuspendUser(account.id))}>{busy ? 'Lifting…' : 'Lift suspension'}</button>
            : account.canSuspend && !asking && <button type="button" className="btn sm" onClick={() => setAsking(true)}>Suspend</button>}
        </div>
      </div>
      {signals.some(Boolean) && (
        <div className="wrap-row" style={{ ['--gap' as string]: '8px' }}>
          {signals.filter(Boolean).map((text) => <span key={String(text)} className="tag cream">{text}</span>)}
        </div>
      )}
      {account.suspendedAt && (
        <p className="small"><strong>Suspended {dayOf(account.suspendedAt)}:</strong> {account.suspendedReason}</p>
      )}
      {!account.canSuspend && <p className="hint">Every demo visitor logs in with this account, so it can’t be suspended here.</p>}
      {asking && (
        <form className="stack" style={gap(10)} onSubmit={suspend} noValidate>
          <div className="field">
            <label htmlFor={id}>Why are you suspending {account.username}?</label>
            <textarea id={id} className={`input${touched && tooShort ? ' bad' : ''}`} rows={2} maxLength={300} value={reason} autoFocus
              onChange={(e) => setReason(e.target.value)} aria-invalid={touched && tooShort ? true : undefined} aria-describedby={`${id}-hint`}
              style={{ resize: 'vertical', padding: '10px 14px', height: 'auto' }} />
            {touched && tooShort
              ? <span id={`${id}-hint`} role="alert" className="err">Give a reason, in a sentence.</span>
              : <span id={`${id}-hint`} className="hint">
                  They’ll see this when they try to log in. {isStore
                    ? 'The store’s listings come down, and pickups waiting there are cancelled and the buyers told.'
                    : 'Anything they’ve reserved goes back on sale.'}
                </span>}
          </div>
          <div className="wrap-row" style={{ ['--gap' as string]: '8px' }}>
            <button type="submit" className="btn sm orange" disabled={busy}>{busy ? 'Suspending…' : 'Suspend account'}</button>
            <button type="button" className="btn sm" disabled={busy} onClick={() => { setAsking(false); setError(null) }}>Cancel</button>
          </div>
        </form>
      )}
      {error && <p role="alert" className="err">{error}</p>}
    </div>
  )
}
