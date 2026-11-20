import { useState, type FormEvent } from 'react'
import { ApiError, api } from '../api/client'
import { useAuth, homeFor } from '../lib/auth-context'
import { messageOf } from '../lib/errors'
import { plural } from '../lib/format'
import { href, navigate, paths } from '../lib/route'
import { gap } from '../lib/style'

type Step = 'code' | 'password' | 'done'

/** A pause tells you roughly how long, like the log in screen does. */
function problemOf(err: unknown): string {
  return err instanceof ApiError && err.retryAfter
    ? `${err.message} (about ${Math.ceil(err.retryAfter / 60)} min)`
    : messageOf(err)
}

/** Forgot your password: prove it's you with a recovery code, then choose a new one. */
export function Recover() {
  const auth = useAuth()
  const [step, setStep] = useState<Step>('code')
  const [username, setUsername] = useState('')
  const [code, setCode] = useState('')
  const [password, setPassword] = useState('')
  const [again, setAgain] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [codesLeft, setCodesLeft] = useState(0)

  const checkCode = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await api.recover(username.trim(), code)   // only checks; the code isn't used up yet
      setStep('password')
    } catch (err) {
      setError(problemOf(err))
    } finally {
      setBusy(false)
    }
  }

  const save = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (password !== again) return setError('The two passwords don’t match.')
    setBusy(true)
    try {
      const result = await api.recover(username.trim(), code, password)
      setCodesLeft(result.recoveryCodesLeft)
      if (result.token && result.expiresAt && result.user) {
        auth.adopt({ token: result.token, expiresAt: result.expiresAt, user: result.user })
      }
      setStep('done')
    } catch (err) {
      // A store that's still waiting for approval: the password is changed, but there's nothing to log in to yet.
      if (err instanceof ApiError && err.code === 'account_not_approved') return navigate(paths.pending)
      setError(problemOf(err))
      // The code may have been used or paused in the meantime: back to the first step to see why.
      if (err instanceof ApiError && (err.status === 400 || err.status === 429) && !/password/i.test(err.message)) setStep('code')
    } finally {
      setBusy(false)
    }
  }

  const heading = { code: 'Get back into your account', password: 'Choose a new password', done: 'You’re back in' }[step]
  const sub = {
    code: 'ThriftBite doesn’t ask for your email or phone, so a recovery code is how you prove it’s you.',
    password: `Code accepted for ${username.trim()}.`,
    done: `Welcome back, ${username.trim()}.`,
  }[step]

  return (
    <div className="stack" style={gap(20, { maxWidth: 520, margin: '0 auto' })}>
      <div className="stack" style={gap(8)}>
        {step !== 'done' && <span className="tag" style={{ alignSelf: 'flex-start' }}>Step {step === 'code' ? 1 : 2} of 2</span>}
        <h1 className="wide" style={{ fontSize: 'clamp(28px, 4vw, 40px)', lineHeight: 1.1, letterSpacing: -0.5 }}>{heading}</h1>
        <p className="muted" style={{ fontSize: 17 }}>{sub}</p>
      </div>

      {step === 'code' && (
        <form className="card stack" style={gap(16, { padding: 24 })} onSubmit={checkCode}>
          <div className="field">
            <label htmlFor="recover-username">Username</label>
            <input id="recover-username" className="input" autoComplete="username" required maxLength={30}
              value={username} onChange={(e) => setUsername(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="recover-code">Recovery code</label>
            <input id="recover-code" className={`input num${error ? ' bad' : ''}`} autoComplete="off" autoCapitalize="characters" spellCheck={false}
              required maxLength={40} placeholder="XXXX-XXXX-XXXX" value={code} onChange={(e) => setCode(e.target.value)}
              aria-describedby={error ? 'recover-err' : 'recover-hint'} style={{ letterSpacing: 1 }} />
            {error
              ? <span id="recover-err" role="alert" className="err">{error}</span>
              : <span id="recover-hint" className="hint">One of the 8 codes you saved when you signed up. Each code works once.</span>}
          </div>
          <button type="submit" className="btn teal big" disabled={busy}>{busy ? 'Checking…' : 'Continue'}</button>
        </form>
      )}

      {step === 'password' && (
        <form className="card stack" style={gap(16, { padding: 24 })} onSubmit={save}>
          <div className="field">
            <label htmlFor="recover-new">New password</label>
            <input id="recover-new" className="input" type="password" autoComplete="new-password" required minLength={8} maxLength={72}
              value={password} onChange={(e) => setPassword(e.target.value)} aria-describedby="recover-rule" />
            <span id="recover-rule" className="hint">At least 8 characters.</span>
          </div>
          <div className="field">
            <label htmlFor="recover-again">Type it again</label>
            <input id="recover-again" className="input" type="password" autoComplete="new-password" required maxLength={72}
              value={again} onChange={(e) => setAgain(e.target.value)} />
          </div>
          {error && <p role="alert" className="err">{error}</p>}
          <button type="submit" className="btn teal big" disabled={busy}>{busy ? 'Saving…' : 'Save new password'}</button>
        </form>
      )}

      {step === 'done' && (
        <div className="stack" style={gap(16)}>
          <div role="status" className="card notice good">
            <strong>Password changed. You’re logged out everywhere else.</strong>
          </div>
          <p>
            You have {plural(codesLeft, 'recovery code')} left.
            {codesLeft <= 3 && ' Make new ones from your account page before they run out.'}
          </p>
          <div className="wrap-row">
            <a className="btn teal" href={href(homeFor(auth.user))}>{auth.user?.role === 'CONSUMER' ? 'Go to tonight’s deals' : 'Continue'}</a>
            <a className="btn" href={href(paths.account)}>Your account</a>
          </div>
        </div>
      )}

      {step !== 'done' && (
        <p className="small muted">
          No codes? Stores can ask an admin to check it’s them. Buyers can make a new account; past reservations stay with the old one.{' '}
          <a href={href(paths.login)}>Back to log in</a>
        </p>
      )}
    </div>
  )
}
