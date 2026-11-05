import { useState, type FormEvent } from 'react'
import thryxFull from '../assets/thryx-full.png'
import thryxHead from '../assets/thryx-head.png'
import { Tabs } from '../components/Tabs'
import { href, navigate } from '../lib/route'

type Mode = 'login' | 'signup'

export function Auth() {
  const [mode, setMode] = useState<Mode>('login')
  const [accountType, setAccountType] = useState<'CONSUMER' | 'OWNER'>('CONSUMER')
  const signup = mode === 'signup'

  // Draft: no API call yet. Wire to POST /api/auth/login and /api/auth/register (src/api/client.ts).
  const submit = (e: FormEvent) => {
    e.preventDefault()
    navigate(accountType === 'OWNER' && signup ? '/login' : '/')
  }

  return (
    <div style={{ minHeight: '100vh', display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) minmax(0, 1fr)' }}>
      <section style={{ padding: '32px 48px', display: 'flex', flexDirection: 'column', gap: 32 }}>
        <a className="brand" href={href('/')} style={{ alignSelf: 'flex-start' }}>
          <img src={thryxHead} alt="" width={40} height={40} />
          <strong style={{ fontSize: 17 }}>ThriftBite</strong>
        </a>
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center', gap: 24, maxWidth: 520 }}>
          <img src={thryxFull} alt="Thryx, the ThriftBite mascot: a teal fox with a leaf sprout, holding a burger"
            width={340} style={{ height: 'auto' }} />
          <div className="stack" style={{ gap: 8 }}>
            <p style={{ fontSize: 28, lineHeight: '34px', fontWeight: 700, letterSpacing: -0.3 }}>Good food, saved before closing.</p>
            <p style={{ fontSize: 16, lineHeight: '24px', color: 'var(--ink-2)' }}>
              Baguio bakeries and carinderias list what's left at the end of the day, at a portion of the usual price.
              You reserve it, pick it up, and pay at the counter.
            </p>
          </div>
        </div>
      </section>

      <main className="sheet" style={{ margin: '8px 8px 8px 0', alignItems: 'center', justifyContent: 'center', padding: '48px 24px' }}>
        <div className="stack" style={{ width: '100%', maxWidth: 400, gap: 24 }}>
          <h1 className="title">{signup ? 'Create your account' : 'Welcome back'}</h1>
          <Tabs label="Account" selected={mode} onSelect={setMode}
            items={[{ id: 'login', label: 'Log in' }, { id: 'signup', label: 'Sign up' }]} />

          <form className="stack" style={{ gap: 16 }} onSubmit={submit}>
            <div className="field">
              <label htmlFor="username">Username</label>
              <input id="username" className="input" name="username" autoComplete="username" required minLength={3} maxLength={30} />
            </div>
            <div className="field">
              <label htmlFor="password">Password</label>
              <input id="password" className="input" name="password" type="password" required
                autoComplete={signup ? 'new-password' : 'current-password'}
                minLength={signup ? 8 : undefined} maxLength={72}
                aria-describedby={signup ? 'pw-rule' : undefined} />
              {signup && <span id="pw-rule" className="small muted">At least 8 characters.</span>}
            </div>

            {signup && (
              <fieldset style={{ border: 0, margin: 0, padding: 0, display: 'flex', flexDirection: 'column', gap: 8 }}>
                <legend style={{ fontWeight: 500, marginBottom: 8 }}>I'm signing up to</legend>
                <label className="choice">
                  <input type="radio" name="accountType" value="CONSUMER" checked={accountType === 'CONSUMER'} onChange={() => setAccountType('CONSUMER')} />
                  <span className="stack"><span style={{ fontWeight: 600 }}>Buy food</span><span className="small muted">You can reserve right away.</span></span>
                </label>
                <label className="choice">
                  <input type="radio" name="accountType" value="OWNER" checked={accountType === 'OWNER'} onChange={() => setAccountType('OWNER')} />
                  <span className="stack"><span style={{ fontWeight: 600 }}>Sell surplus from my store</span><span className="small muted">An admin approves new stores before they can log in.</span></span>
                </label>
              </fieldset>
            )}

            <button type="submit" className="btn-primary" style={{ marginTop: 8, minHeight: 44 }}>{signup ? 'Create account' : 'Log in'}</button>
            {!signup && (
              <p className="small muted">New here? <button type="button" className="btn-text small" style={{ minHeight: 0 }} onClick={() => setMode('signup')}>Create an account</button></p>
            )}
          </form>
        </div>
      </main>
    </div>
  )
}
