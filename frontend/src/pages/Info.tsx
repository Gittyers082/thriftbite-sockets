import thryxFull from '../assets/thryx-full.png'
import { useAuth } from '../lib/auth-context'
import { href, navigate, paths } from '../lib/route'
import { gap } from '../lib/style'

/** After an owner signs up, or when an unapproved store tries to log in. */
export function Pending() {
  const steps: [string, string, string?][] = [
    ['Signed up', 'Your store has been created.', 'Done'],
    ['An admin checks your store', 'Usually within 1 day. They may call the number on your storefront.', 'Now'],
    ['Log in and post your first listing', 'Fill in your store profile first so buyers can find you.'],
  ]
  return (
    <div className="pending-layout">
      <div className="stack" style={gap(22)}>
        <h1 className="wide" style={{ fontSize: 'clamp(30px, 4vw, 48px)', lineHeight: 1.08, letterSpacing: -1 }}>Your store is waiting for approval.</h1>
        <p style={{ fontSize: 18, lineHeight: '28px', maxWidth: 620 }}>
          An admin checks every new store before it can list food, so buyers know the store is real.
          You’ll be able to log in as soon as it’s approved.
        </p>
        <ol className="card" style={{ listStyle: 'none', margin: 0, padding: 0, maxWidth: 640 }}>
          {steps.map(([title, note, state], i) => (
            <li key={title} className="card-row row" style={{ alignItems: 'flex-start' }}>
              <span aria-hidden="true" className="wide avatar" style={{ width: 36, height: 36, background: i === 0 ? 'var(--lime)' : i === 1 ? 'var(--orange)' : 'var(--paper)' }}>{i + 1}</span>
              <div className="grow"><strong>{title}</strong><p className="muted small">{note}</p></div>
              {state && <span className={`tag ${i === 0 ? 'lime' : 'orange'}`}>{state}</span>}
            </li>
          ))}
        </ol>
        <div className="wrap-row">
          <a className="btn" href={href(paths.login)}>Back to log in</a>
          <a className="btn" href={href(paths.browse)}>See what stores post</a>
        </div>
      </div>
      <img src={thryxFull} alt="" width={260} height={255} className="hide-phone" />
    </div>
  )
}

export function Me() {
  const { user, logout } = useAuth()
  if (!user) {
    return <a className="btn teal" href={href(paths.login)}>Log in</a>
  }
  const role = { CONSUMER: 'Buyer', OWNER: 'Store owner', ADMIN: 'Admin' }[user.role]
  return (
    <div className="stack" style={gap(24, { maxWidth: 560 })}>
      <div className="page-head"><div className="stack" style={gap(6)}><h1 className="wide">{user.username}</h1><p className="muted">{role}</p></div></div>
      <section className="card">
        <a className="card-row spread" href={href(paths.privacy)} style={{ textDecoration: 'none', fontWeight: 600 }}>Privacy notice <span aria-hidden="true">›</span></a>
        <button type="button" className="card-row" onClick={() => { logout(); navigate(paths.browse) }}
          style={{ all: 'unset', boxSizing: 'border-box', display: 'flex', width: '100%', minHeight: 52, padding: '0 20px', alignItems: 'center', borderTop: '1px solid var(--rule)', fontWeight: 700, cursor: 'pointer' }}>
          Log out
        </button>
      </section>
      <p className="hint">Saved savings, pickup record and recovery codes are coming in a later update.</p>
    </div>
  )
}

export function NotFound() {
  return (
    <div className="pending-layout">
      <div className="stack" style={gap(18)}>
        <span className="tag orange tilt" style={{ alignSelf: 'flex-start', height: 32, fontSize: 15 }}>404</span>
        <h1 className="wide" style={{ fontSize: 'clamp(34px, 5vw, 60px)', lineHeight: 1.04, letterSpacing: -1.5 }}>This page isn’t on the menu.</h1>
        <p style={{ fontSize: 18, lineHeight: '28px', maxWidth: 560 }}>The link may be old, or the listing was taken down after it sold out. Tonight’s deals are still a tap away.</p>
        <div className="wrap-row">
          <a className="btn teal" href={href(paths.browse)}>See tonight’s deals</a>
          <a className="btn" href={href(paths.reservations)}>Your reservations</a>
        </div>
      </div>
      <img src={thryxFull} alt="Thryx, the ThriftBite mascot, holding a burger" width={320} height={314} />
    </div>
  )
}

const privacy: [string, string][] = [
  ['What we collect', 'Your username, your password (stored only as a one-way hash), your reservations, and the stores you deal with. For stores, also the store’s name, address and listings. We don’t ask for your email, phone number or payment details, and we don’t take payments.'],
  ['Why we collect it', 'To run reservations and pickups, to show stores the orders they need to hand over, to stop accounts that keep reserving without picking up, and to keep ThriftBite safe from fake stores and abuse.'],
  ['Who can see it', 'A store sees your username and what you reserved, only for reservations with that store. Admins can see accounts and reservations. We don’t sell or share your data with advertisers.'],
  ['How long we keep it', 'Your account and reservations stay until you delete your account. The public demo resets every few hours and deletes everything in it.'],
  ['Your rights', 'Under the Data Privacy Act of 2012 (Republic Act No. 10173), you can ask what we hold about you, correct it, download it, object to how it’s used, or have it deleted. You can also complain to the National Privacy Commission.'],
  ['Contact', 'Questions about your data go to [PRIVACY CONTACT EMAIL]. ThriftBite is a student project built in Baguio City.'],
]

export function Privacy() {
  return (
    <article className="stack" style={gap(22, { maxWidth: 700 })}>
      <div className="stack" style={gap(10)}>
        <h1 className="wide" style={{ fontSize: 'clamp(30px, 4vw, 48px)', lineHeight: 1.08, letterSpacing: -1 }}>Privacy notice</h1>
        <p className="muted">Last updated Nov 7, 2026</p>
        <p style={{ fontSize: 19, lineHeight: '29px' }}>ThriftBite keeps as little about you as it can and still run pickups. Here’s what that is, and what you can do about it.</p>
      </div>
      {privacy.map(([title, text]) => (
        <section key={title} className="stack" style={gap(8, { paddingTop: 20, borderTop: '2px solid var(--ink)' })}>
          <h2 className="wide" style={{ fontSize: 22 }}>{title}</h2>
          <p style={{ fontSize: 17, lineHeight: '27px' }}>{text}</p>
        </section>
      ))}
      <section className="panel cream stack" style={gap(6, { padding: '18px 20px' })}>
        <strong>About the food</strong>
        <p>Stores make and sell the food, and they’re responsible for it. ThriftBite connects you with them but doesn’t cook, store or deliver anything.</p>
      </section>
    </article>
  )
}
