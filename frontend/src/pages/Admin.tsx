import { useState } from 'react'
import thryxFull from '../assets/thryx-full.png'
import { Shell } from '../components/Shell'
import { pendingOwners, tints } from '../data/sample'
import { plural } from '../lib/format'

export function Admin() {
  const [approved, setApproved] = useState<string[]>([])

  // Draft: local only. Wire to POST /api/admin/users/{id}/approve.
  const pending = pendingOwners.filter((o) => !approved.includes(o.username))
  const last = approved[approved.length - 1]

  return (
    <Shell area="admin" current="/admin" counts={{ '/admin': String(pending.length) }}>
      <div className="titles stack" style={{ gap: 4 }}>
        <h1 className="title">Store approvals</h1>
        <p className="subline">
          {pending.length === 0
            ? 'Every store has been reviewed.'
            : `${plural(pending.length, 'store is', 'stores are')} waiting. The oldest signed up ${pending[0].when.toLowerCase()}.`}
        </p>
      </div>

      <div className="with-rail">
        <section aria-label="Stores waiting for approval" style={{ minWidth: 0 }}>
          {pending.length > 0 ? (
            <table className="table">
              <thead>
                <tr><th scope="col">Store account</th><th scope="col">Signed up</th><th scope="col"><span className="sr-only">Approve</span></th></tr>
              </thead>
              <tbody>
                {pending.map((o, i) => (
                  <tr key={o.username}>
                    <td>
                      <div className="food">
                        <span className="avatar lg" aria-hidden="true" style={{ background: tints[i % tints.length].bg, color: tints[i % tints.length].ink }}>
                          {o.username.charAt(0).toUpperCase()}
                        </span>
                        <span style={{ fontWeight: 600 }}>{o.username}</span>
                      </div>
                    </td>
                    <td><div className="stack"><span className="num">{o.signedUp}</span><span className="small muted">{o.when}</span></div></td>
                    <td className="end">
                      <button type="button" className="btn-ghost md" aria-label={`Approve ${o.username}`}
                        onClick={() => setApproved((a) => [...a, o.username])}>Approve</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <div className="empty">
              <img src={thryxFull} alt="Thryx, the ThriftBite mascot, holding a burger" />
              <p style={{ fontWeight: 600, fontSize: 15 }}>No stores waiting. New sign-ups show up here.</p>
            </div>
          )}
          {last && <p role="status" className="pill good" style={{ marginTop: 16, height: 32, padding: '0 14px', fontSize: 14 }}>Approved {last}. They can log in now.</p>}
        </section>

        <aside className="panel" aria-labelledby="why-h" style={{ gap: 10, padding: 20 }}>
          <h2 id="why-h" className="card-title">Before you approve</h2>
          <p style={{ color: 'var(--ink-2)' }}>Approved owners can log in, list food and take reservations right away. Buyers don't need approval.</p>
          <p style={{ color: 'var(--ink-2)' }}>Check that the account belongs to a real store in Baguio, for example by calling the number on its storefront.</p>
        </aside>
      </div>
    </Shell>
  )
}
