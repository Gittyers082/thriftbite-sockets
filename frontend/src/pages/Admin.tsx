import { useCallback, useState } from 'react'
import { api } from '../api/client'
import type { UserResponse } from '../api/types'
import { Empty, Loading, ProblemPanel } from '../components/States'
import { messageOf } from '../lib/errors'
import { useAuth } from '../lib/auth-context'
import { plural } from '../lib/format'
import { gap } from '../lib/style'
import type { LiveMessage } from '../lib/live'
import { useLiveReload } from '../lib/useLive'
import { useLoad } from '../lib/useLoad'

/** A store signed up, or another admin approved one. */
const approvalsChanged = (message: LiveMessage) => message.type === 'approvals'

/** New stores wait here until an admin lets them in. */
export function Admin() {
  const { user } = useAuth()
  const isDemo = user?.username === 'demo.admin'
  const loaded = useLoad(useCallback(() => api.pendingOwners(), []))
  useLiveReload(loaded.reload, approvalsChanged)
  const [approved, setApproved] = useState<UserResponse[]>([])
  const [busy, setBusy] = useState<string | null>(null)
  const [problem, setProblem] = useState<string | null>(null)

  if (loaded.error && !loaded.data) return <ProblemPanel error={loaded.error} onRetry={loaded.reload} />
  if (!loaded.data) return <Loading label="Loading stores waiting for approval…" />

  const waiting = loaded.data.filter((u) => !approved.some((a) => a.id === u.id))

  const approve = async (user: UserResponse) => {
    setProblem(null)
    setBusy(user.id)
    try {
      const done = await api.approve(user.id)
      setApproved((list) => [done, ...list])
    } catch (error) {
      setProblem(messageOf(error))
    } finally {
      setBusy(null)
    }
  }

  return (
    <div className="stack" style={gap(24, { maxWidth: 820 })}>
      <div className="page-head">
        <div className="stack" style={gap(6)}>
          <h1 className="wide">Store approvals</h1>
          <p className="muted">{waiting.length === 0 ? 'No stores waiting' : `${plural(waiting.length, 'store')} waiting`}</p>
        </div>
      </div>
      {isDemo && (
        <p className="panel notice dark">
          You’re the demo admin, so you only see the demo’s sample stores. Real sign-ups need a real admin.
        </p>
      )}
      <p className="panel cream" style={{ padding: '16px 20px' }}>
        Before approving, check that the store is real: look it up, or call the number on its storefront. Approved stores can post food right away.
      </p>
      {problem && <p role="alert" className="err">{problem}</p>}

      {waiting.length === 0 ? (
        <Empty title="All caught up">New store sign-ups will show up here.</Empty>
      ) : (
        <section className="card">
          {waiting.map((user, i) => (
            <div key={user.id} className="card-row spread" style={{ borderTop: i === 0 ? 0 : undefined, padding: '14px 20px' }}>
              <div className="row" style={gap(12)}>
                <span className="avatar" aria-hidden="true" style={{ background: 'var(--orange)' }}>{user.username.charAt(0)}</span>
                <div><strong>{user.username}</strong><p className="hint">Store owner</p></div>
              </div>
              <button type="button" className="btn sm lime" disabled={busy === user.id} onClick={() => approve(user)}>
                {busy === user.id ? 'Approving…' : 'Approve'}
              </button>
            </div>
          ))}
        </section>
      )}

      {approved.length > 0 && (
        <section className="stack" style={gap(10)}>
          <h2 className="wide" style={{ fontSize: 20 }}>Approved just now</h2>
          <ul className="card" style={{ listStyle: 'none', margin: 0, padding: 0 }}>
            {approved.map((user) => (
              <li key={user.id} className="card-row spread" style={{ padding: '12px 20px' }}>
                <strong>{user.username}</strong><span className="tag teal">Approved</span>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  )
}
