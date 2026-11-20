import { useCallback, useState, type FormEvent } from 'react'
import { ApiError, api } from '../api/client'
import type { AccountSummary } from '../api/types'
import { RecoveryCodeList } from '../components/RecoveryCodeList'
import { Loading, ProblemPanel } from '../components/States'
import { useAuth } from '../lib/auth-context'
import { messageOf } from '../lib/errors'
import { dateLabel, dayOf, monthAndYear, monthShort, peso, pesoShort, plural, timeOf } from '../lib/format'
import { reportKindLabel } from '../lib/reports'
import { href, navigate, paths } from '../lib/route'
import { gap } from '../lib/style'
import { useLoad } from '../lib/useLoad'

const since = { CONSUMER: 'Buying since', OWNER: 'Store account since', ADMIN: 'Admin since' } as const
const avatarColor = { CONSUMER: 'var(--lime)', OWNER: 'var(--orange)', ADMIN: 'var(--ink)' } as const

function problemOf(err: unknown): string {
  return err instanceof ApiError && err.retryAfter
    ? `${err.message} (about ${Math.ceil(err.retryAfter / 60)} min)`
    : messageOf(err)
}

/** Your account: savings and pickup record for buyers, sign-in settings, and your data. */
export function Account() {
  const { logout } = useAuth()
  const loaded = useLoad(useCallback(() => api.account(), []))
  if (loaded.error && !loaded.data) return <ProblemPanel error={loaded.error} onRetry={loaded.reload} />
  if (!loaded.data) return <Loading label="Loading your account…" />
  const account = loaded.data

  return (
    <div className="stack" style={gap(24, { maxWidth: 760 })}>
      <div className="page-head">
        <div className="row" style={gap(14)}>
          <span className="avatar" aria-hidden="true"
            style={{ width: 56, height: 56, fontSize: 24, background: avatarColor[account.role], color: account.role === 'ADMIN' ? '#fff' : undefined }}>
            {account.username.charAt(0)}
          </span>
          <div className="stack" style={gap(2)}>
            <h1 className="wide">{account.username}</h1>
            <p className="muted">{since[account.role]} {monthAndYear(account.memberSince)}</p>
          </div>
        </div>
        <button type="button" className="btn" onClick={() => { logout(); navigate(paths.browse) }}>Log out</button>
      </div>

      {account.buyer && <Savings buyer={account.buyer} />}
      {account.buyer && <PickupRecord buyer={account.buyer} />}
      {account.buyer && <MyReports />}

      <section className="card">
        <div className="card-head"><h2>Sign-in and security</h2></div>
        {account.demo ? (
          <p className="card-row">
            This is a shared demo account, so its password and recovery codes can’t be changed.{' '}
            <a href={href(paths.signup)}>Sign up for your own account</a> to try those.
          </p>
        ) : (
          <>
            <PasswordForm />
            <Codes account={account} onMade={loaded.reload} />
          </>
        )}
      </section>

      <YourData account={account} />
    </div>
  )
}

function Savings({ buyer }: { buyer: NonNullable<AccountSummary['buyer']> }) {
  const top = Math.max(1, ...buyer.months.map((m) => m.saved))
  return (
    <section className="card" style={{ background: 'var(--lime)' }}>
      <div className="two" style={{ padding: 24, gap: 24, alignItems: 'center' }}>
        <div className="stack" style={gap(6)}>
          <span style={{ fontWeight: 700 }}>You’ve saved</span>
          <strong className="wide num" style={{ fontSize: 'clamp(36px, 6vw, 56px)', lineHeight: 1 }}>{peso(buyer.saved)}</strong>
          <p>
            {buyer.mealsSaved === 0
              ? 'Pick up your first reservation and your savings show up here.'
              : `on ${plural(buyer.mealsSaved, 'meal')} that would have been thrown out. That’s the usual price minus what you paid.`}
          </p>
        </div>
        <div className="months" role="img" aria-label={`Saved by month: ${buyer.months.map((m) => `${monthShort(m.month)} ${pesoShort(m.saved)}`).join(', ')}`}>
          {buyer.months.map((m) => (
            <div key={m.month} className="col">
              <span className="num small" style={{ fontWeight: 700 }}>{m.saved > 0 ? pesoShort(m.saved) : ''}</span>
              <div className="bar-fill" style={{ height: `${(m.saved / top) * 100}%` }} />
              <span className="small">{monthShort(m.month)}</span>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}

function PickupRecord({ buyer }: { buyer: NonNullable<AccountSummary['buyer']> }) {
  const missed = buyer.missedPickups
  return (
    <section className="card">
      <div className="card-head"><h2>Pickup record</h2></div>
      <div className="card-row stack" style={gap(6)}>
        <strong>{missed.length === 0 ? 'No missed pickups in the last 30 days' : `${plural(missed.length, 'missed pickup')} in the last 30 days`}</strong>
        {missed.length > 0 && <p className="muted">{missed.map((m) => `${m.storeName}, ${dateLabel(m.day).split(', ')[1]}`).join('; ')}.</p>}
        {buyer.reserveBlockedUntil && (
          <p role="status" className="panel notice bad" style={{ padding: '12px 14px' }}>
            <span>
              <strong>You can’t reserve until {dayOf(buyer.reserveBlockedUntil)}, {timeOf(buyer.reserveBlockedUntil)}.</strong>{' '}
              That’s {buyer.missesBeforeBlock} missed pickups within {buyer.missWindowDays} days. You can still browse and follow stores.
            </span>
          </p>
        )}
        <p className="small">
          A store set that food aside for you. Miss {buyer.missesBeforeBlock} pickups within {buyer.missWindowDays} days and
          reserving is paused for {buyer.blockDays} days. Cancelling before the pickup window starts never counts, and if a
          miss wasn’t your fault, report a problem with that pickup from your reservations.
        </p>
      </div>
      <p className="card-row">
        You can hold <strong>{plural(buyer.maxActiveReservations, 'reservation')}</strong> at a time.{' '}
        {buyer.activeReservations === 0 ? 'You have none now.' : `You have ${buyer.activeReservations} now.`}{' '}
        <a href={href(paths.reservations)}>See your reservations</a>
      </p>
    </section>
  )
}

/** Problems the buyer reported, and what came of them. Nothing shows until there's at least one. */
function MyReports() {
  const loaded = useLoad(useCallback(() => api.myReports(), []))
  if (!loaded.data || loaded.data.length === 0) return null
  return (
    <section className="card" id="reports">
      <div className="card-head"><h2>Problems you reported</h2></div>
      {loaded.data.map((report) => (
        <div key={report.id} className="report-row stack" style={gap(6)}>
          <div className="spread" style={{ flexWrap: 'wrap', gap: 8 }}>
            <strong>{reportKindLabel(report.kind)}</strong>
            <span className={`tag ${report.status === 'RESOLVED' ? 'teal' : 'cream'}`}>
              {report.status === 'RESOLVED' ? 'Answered' : 'We’re looking into it'}
            </span>
          </div>
          <span className="muted small">{report.storeName}, {dayOf(report.createdAt)}</span>
          {report.details && <p>“{report.details}”</p>}
          {report.reply && <p className="panel cream" style={{ padding: '10px 14px' }}><strong>Our reply:</strong> {report.reply}</p>}
        </div>
      ))}
    </section>
  )
}

function PasswordForm() {
  const { adopt } = useAuth()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setDone(false)
    setBusy(true)
    try {
      adopt(await api.changePassword(current, next))   // this device gets a fresh login; the others are logged out
      setCurrent('')
      setNext('')
      setDone(true)
    } catch (err) {
      setError(problemOf(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="card-row stack" style={gap(14)} onSubmit={submit}>
      <div className="two" style={{ gap: 14 }}>
        <div className="field">
          <label htmlFor="pw-current">Current password</label>
          <input id="pw-current" className="input" type="password" autoComplete="current-password" required maxLength={72}
            value={current} onChange={(e) => setCurrent(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="pw-new">New password</label>
          <input id="pw-new" className="input" type="password" autoComplete="new-password" required minLength={8} maxLength={72}
            value={next} onChange={(e) => setNext(e.target.value)} aria-describedby="pw-new-rule" />
          <span id="pw-new-rule" className="hint">At least 8 characters.</span>
        </div>
      </div>
      {error && <p role="alert" className="err">{error}</p>}
      <div className="wrap-row" style={{ alignItems: 'center' }}>
        <button type="submit" className="btn" disabled={busy}>{busy ? 'Changing…' : 'Change password'}</button>
        {done && <span role="status" style={{ fontWeight: 700 }}>Password changed. Your other devices are logged out.</span>}
      </div>
    </form>
  )
}

function Codes({ account, onMade }: { account: AccountSummary; onMade: () => void }) {
  const [asking, setAsking] = useState(false)
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [codes, setCodes] = useState<string[] | null>(null)

  const make = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      setCodes((await api.newRecoveryCodes(password)).codes)
      setAsking(false)
      setPassword('')
      onMade()
    } catch (err) {
      setError(problemOf(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="card-row stack" style={gap(12)}>
      <h3 style={{ fontSize: 17, fontWeight: 800 }}>Recovery codes</h3>
      <p>
        {account.recoveryCodesMadeAt == null
          ? 'You don’t have recovery codes yet.'
          : `You have ${plural(account.recoveryCodesLeft, 'unused code')}, made ${dayOf(account.recoveryCodesMadeAt)}.`}{' '}
        Use one to get back in if you forget your password. ThriftBite has no email or phone number for you.
      </p>
      {codes && (
        <div className="stack" style={gap(10)}>
          <strong role="status">Save these somewhere safe. Your old codes no longer work, and these won’t be shown again.</strong>
          <RecoveryCodeList codes={codes} username={account.username} />
        </div>
      )}
      {asking ? (
        <form className="stack" style={gap(10, { maxWidth: 360 })} onSubmit={make}>
          <div className="field">
            <label htmlFor="codes-password">Your password, to confirm it’s you</label>
            <input id="codes-password" className="input" type="password" autoComplete="current-password" required maxLength={72}
              value={password} onChange={(e) => setPassword(e.target.value)} />
          </div>
          {error && <p role="alert" className="err">{error}</p>}
          <div className="row">
            <button type="submit" className="btn" disabled={busy}>{busy ? 'Making codes…' : 'Make new codes'}</button>
            <button type="button" className="btn" onClick={() => { setAsking(false); setError(null) }}>Cancel</button>
          </div>
        </form>
      ) : (
        <div><button type="button" className="btn" onClick={() => setAsking(true)}>Make new codes</button></div>
      )}
    </div>
  )
}

function YourData({ account }: { account: AccountSummary }) {
  const { logout } = useAuth()
  const [confirming, setConfirming] = useState(false)
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const active = account.buyer?.activeReservations ?? 0
  const canDelete = account.role === 'CONSUMER' && !account.demo
  const canClose = account.store?.owner === true && !account.demo
  const [closing, setClosing] = useState(false)

  const download = async () => {
    setError(null)
    setBusy(true)
    try {
      const data = await api.exportData()
      const url = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' }))
      const link = document.createElement('a')
      link.href = url
      link.download = `thriftbite-${account.username}.json`
      link.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      setError(messageOf(err))
    } finally {
      setBusy(false)
    }
  }

  const remove = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await api.deleteAccount(password)
      logout()
      navigate(paths.browse)
    } catch (err) {
      setError(problemOf(err))
      setBusy(false)
    }
  }

  const close = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await api.closeStore(password)
      logout()
      navigate(paths.browse)
    } catch (err) {
      setError(problemOf(err))
      setBusy(false)
    }
  }

  return (
    <section className="card">
      <div className="card-head"><h2>Your data</h2></div>
      <div className="card-row stack" style={gap(12)}>
        <p>
          {account.role === 'OWNER'
            ? 'ThriftBite keeps your username, your store’s profile and listings, and the reservations buyers make with your store.'
            : 'ThriftBite keeps your username, reservations and pickup record. Stores see your username only on reservations you make with them.'}{' '}
          Read the <a href={href(paths.privacy)}>privacy notice</a> for what we keep and why.
        </p>
        {error && !confirming && !closing && <p role="alert" className="err">{error}</p>}
        <div className="wrap-row">
          <button type="button" className="btn" disabled={busy} onClick={download}>Download my data</button>
          {canDelete && !confirming && <button type="button" className="btn danger" onClick={() => { setConfirming(true); setError(null) }}>Delete account</button>}
          {canClose && !closing && <button type="button" className="btn danger" onClick={() => { setClosing(true); setError(null) }}>Close your store</button>}
        </div>
        {account.store && !account.store.owner && (
          <p className="hint">This is a staff account at {account.store.name}. The store’s owner can give it a new password or remove it.</p>
        )}
        {account.store?.owner && account.demo && <p className="hint">The demo store is shared, so it can’t be closed.</p>}
        {account.role === 'ADMIN' && <p className="hint">Admin accounts can’t be deleted from here.</p>}
        {closing && account.store && (
          <form className="panel stack" style={gap(12, { padding: 16, borderColor: 'var(--bad)' })} onSubmit={close}>
            <strong style={{ fontSize: 18 }}>Close {account.store.name} for good?</strong>
            <p>
              Your listings come down and your store’s page goes. Buyers with a pickup waiting are told not to come. Your
              account and your staff’s accounts are deleted. Past reservations stay in buyers’ records with your store’s name.
              This can’t be undone, so download your data first if you want a copy.
            </p>
            <div className="field" style={{ maxWidth: 360 }}>
              <label htmlFor="close-password">Your password, to confirm it’s you</label>
              <input id="close-password" className="input" type="password" autoComplete="current-password" required maxLength={72}
                value={password} onChange={(e) => setPassword(e.target.value)} />
            </div>
            {error && <p role="alert" className="err">{error}</p>}
            <div className="wrap-row">
              <button type="button" className="btn" onClick={() => { setClosing(false); setError(null); setPassword('') }}>Keep my store</button>
              <button type="submit" className="btn danger" disabled={busy}>{busy ? 'Closing…' : 'Close the store'}</button>
            </div>
          </form>
        )}
        {confirming && (
          <form className="panel stack" style={gap(12, { padding: 16, borderColor: 'var(--bad)' })} onSubmit={remove}>
            <strong style={{ fontSize: 18 }}>Delete your account?</strong>
            <p>
              {active > 0 && `Your ${active === 1 ? 'active reservation is' : `${active} active reservations are`} cancelled and the food goes back on sale. `}
              Past reservations stay in stores’ records without your username. This can’t be undone.
            </p>
            <div className="field" style={{ maxWidth: 360 }}>
              <label htmlFor="delete-password">Your password, to confirm it’s you</label>
              <input id="delete-password" className="input" type="password" autoComplete="current-password" required maxLength={72}
                value={password} onChange={(e) => setPassword(e.target.value)} />
            </div>
            {error && <p role="alert" className="err">{error}</p>}
            <div className="wrap-row">
              <button type="button" className="btn" onClick={() => { setConfirming(false); setError(null); setPassword('') }}>Keep my account</button>
              <button type="submit" className="btn danger" disabled={busy}>{busy ? 'Deleting…' : 'Delete account'}</button>
            </div>
          </form>
        )}
      </div>
    </section>
  )
}
