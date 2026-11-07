import { useCallback, useState, type FormEvent } from 'react'
import { ApiError, api } from '../api/client'
import type { DemoRole } from '../api/types'
import thryxFull from '../assets/thryx-full.png'
import thryxHead from '../assets/thryx-head.png'
import { messageOf } from '../lib/errors'
import { homeFor, useAuth } from '../lib/auth-context'
import { href, navigate, paths } from '../lib/route'
import { gap } from '../lib/style'
import { useLoad } from '../lib/useLoad'

const demoButtons: { as: DemoRole; label: string; className: string }[] = [
  { as: 'BUYER', label: 'Buyer', className: 'btn sm lime' },
  { as: 'OWNER', label: 'Store owner', className: 'btn sm orange' },
  { as: 'ADMIN', label: 'Admin', className: 'btn sm ink' },
]

/** Log in and sign up on one card, plus one-tap demo accounts when the API offers them. */
export function Auth({ mode }: { mode: 'login' | 'signup' }) {
  const auth = useAuth()
  const demo = useLoad(useCallback(() => api.demoInfo().catch(() => null), []))
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [accountType, setAccountType] = useState<'CONSUMER' | 'OWNER'>('CONSUMER')
  const [storeName, setStoreName] = useState('')
  const [agreed, setAgreed] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const signup = mode === 'signup'

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (signup && !agreed) return setError('Please agree to the privacy notice to create an account.')
    setBusy(true)
    try {
      if (signup) {
        const user = await auth.register({
          username, password, accountType, storeName: accountType === 'OWNER' ? storeName.trim() || undefined : undefined,
        })
        navigate(user.approved ? homeFor(user) : paths.pending)
      } else {
        navigate(homeFor(await auth.login(username, password)))
      }
    } catch (err) {
      if (err instanceof ApiError && err.code === 'account_not_approved') return navigate(paths.pending)
      if (err instanceof ApiError && err.retryAfter) {
        setError(`${err.message} (about ${Math.ceil(err.retryAfter / 60)} min)`)
      } else {
        setError(messageOf(err))
      }
    } finally {
      setBusy(false)
    }
  }

  const tryDemo = async (as: DemoRole) => {
    setError(null)
    setBusy(true)
    try {
      navigate(homeFor(await auth.demoLogin(as)))
    } catch (err) {
      setError(messageOf(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page" style={{ background: 'var(--teal)' }}>
      <header className="wrap" style={{ height: 64, display: 'flex', alignItems: 'center' }}>
        <a className="mark" href={href(paths.browse)} style={{ color: '#fff' }}>
          <img src={thryxHead} alt="" width={40} height={40} />
          <span style={{ color: '#fff' }}>ThriftBite</span>
        </a>
      </header>
      <main className="wrap stack" style={gap(0, { alignItems: 'center', paddingBottom: 48 })}>
        <img src={thryxFull} alt="Thryx, the ThriftBite mascot, holding a burger" width={170} height={167}
          style={{ width: 170, marginBottom: -30, position: 'relative', zIndex: 1 }} />
        <div className="card stack" style={gap(20, { width: '100%', maxWidth: 460, padding: '40px 28px 28px' })}>
          <div className="stack" style={gap(6, { textAlign: 'center' })}>
            <h1 className="wide" style={{ fontSize: 28, lineHeight: '32px' }}>{signup ? 'Join ThriftBite' : 'Welcome back'}</h1>
            <p className="muted">{signup ? 'Takes a minute. No card needed.' : 'Log in to reserve tonight’s surplus.'}</p>
          </div>

          <div role="tablist" aria-label="Account" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr' }}>
            <a role="tab" className="chip" aria-selected={!signup} href={href(paths.login)} style={{ justifyContent: 'center', textDecoration: 'none' }}>Log in</a>
            <a role="tab" className="chip" aria-selected={signup} href={href(paths.signup)} style={{ justifyContent: 'center', textDecoration: 'none', marginLeft: -2 }}>Sign up</a>
          </div>

          <form className="stack" style={gap(16)} onSubmit={submit}>
            <div className="field">
              <label htmlFor="username">Username</label>
              <input id="username" className="input" autoComplete="username" required minLength={3} maxLength={30}
                value={username} onChange={(e) => setUsername(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="password">Password</label>
              <input id="password" className="input" type="password" required minLength={signup ? 8 : 1} maxLength={72}
                autoComplete={signup ? 'new-password' : 'current-password'}
                value={password} onChange={(e) => setPassword(e.target.value)} aria-describedby={signup ? 'pw-rule' : undefined} />
              {signup && <span id="pw-rule" className="hint">At least 8 characters.</span>}
            </div>

            {signup && (
              <>
                <fieldset className="stack" style={gap(10, { border: 0, margin: 0, padding: 0 })}>
                  <legend style={{ fontWeight: 700, marginBottom: 10 }}>I’m signing up to</legend>
                  <label className="choice">
                    <input type="radio" name="type" checked={accountType === 'CONSUMER'} onChange={() => setAccountType('CONSUMER')} />
                    <span className="stack" style={gap(0)}><strong>Buy food</strong><span className="small">You can reserve right away.</span></span>
                  </label>
                  <label className="choice">
                    <input type="radio" name="type" checked={accountType === 'OWNER'} onChange={() => setAccountType('OWNER')} />
                    <span className="stack" style={gap(0)}><strong>Sell surplus from my store</strong><span className="small">An admin approves new stores before they can log in.</span></span>
                  </label>
                </fieldset>
                {accountType === 'OWNER' && (
                  <div className="field">
                    <label htmlFor="store">Store name</label>
                    <input id="store" className="input" maxLength={80} placeholder="Sunrise Bakery"
                      value={storeName} onChange={(e) => setStoreName(e.target.value)} />
                  </div>
                )}
                <label className="row small" style={{ alignItems: 'flex-start', cursor: 'pointer' }}>
                  <input type="checkbox" checked={agreed} onChange={(e) => setAgreed(e.target.checked)}
                    style={{ width: 18, height: 18, margin: '2px 0 0', accentColor: 'var(--ink)', flex: 'none' }} />
                  <span>I agree to the <a href={href(paths.privacy)}>privacy notice</a>. Stores, not ThriftBite, are responsible for the food they sell.</span>
                </label>
              </>
            )}

            {error && <p role="alert" className="err">{error}</p>}
            <button type="submit" className="btn teal big" disabled={busy}>
              {busy ? 'One moment…' : signup ? 'Create account' : 'Log in'}
            </button>
          </form>

          {demo.data && (
            <div className="stack" style={gap(12, { borderTop: '2px solid var(--ink)', paddingTop: 20 })}>
              <div className="spread">
                <strong>Just looking? Try the demo</strong>
                <span className="hint">Resets every few hours</span>
              </div>
              <div className="three" style={{ gap: 10 }}>
                {demoButtons.map((b) => (
                  <button key={b.as} type="button" className={b.className} disabled={busy} onClick={() => tryDemo(b.as)}>{b.label}</button>
                ))}
              </div>
            </div>
          )}
        </div>
        <p style={{ marginTop: 24, color: '#fff', textAlign: 'center', maxWidth: 460 }}>
          End-of-day food from Baguio bakeries and carinderias, at a portion of the usual price.
        </p>
      </main>
    </div>
  )
}
