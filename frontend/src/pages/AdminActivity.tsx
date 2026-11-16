import { useCallback } from 'react'
import { api } from '../api/client'
import type { LiveMessage } from '../lib/live'
import { Empty, Loading, ProblemPanel } from '../components/States'
import { dayOf, timeOf } from '../lib/format'
import { gap } from '../lib/style'
import { useLiveReload } from '../lib/useLive'
import { useLoad } from '../lib/useLoad'

const actions: Record<string, string> = {
  APPROVE_STORE: 'Approved a store',
  ANSWER_REPORT: 'Answered a report',
  SUSPEND_ACCOUNT: 'Suspended an account',
  LIFT_SUSPENSION: 'Lifted a suspension',
}

/** Anything an admin does adds a line, so the list is worth reloading when admins' work changes. */
const adminWork = (message: LiveMessage) => message.type === 'approvals' || message.type === 'reports'

/** The record of what admins did. Lines can't be edited or removed. */
export function AdminActivity() {
  const loaded = useLoad(useCallback(() => api.adminActivity(), []))
  useLiveReload(loaded.reload, adminWork)

  if (loaded.error && !loaded.data) return <ProblemPanel error={loaded.error} onRetry={loaded.reload} />
  if (!loaded.data) return <Loading label="Loading activity…" />

  return (
    <div className="stack" style={gap(24)}>
      <div className="page-head">
        <div className="stack" style={gap(6)}>
          <h1 className="wide">Activity</h1>
          <p className="muted">What admins did, newest first. Lines can’t be changed or removed.</p>
        </div>
      </div>
      {loaded.data.length === 0 ? (
        <Empty title="Nothing yet">Approving a store, answering a report or suspending an account adds a line here.</Empty>
      ) : (
        <section className="card tbl-scroll">
          <table className="tbl">
            <thead>
              <tr><th scope="col">When</th><th scope="col">Admin</th><th scope="col">What</th><th scope="col">About</th><th scope="col">Note</th></tr>
            </thead>
            <tbody>
              {loaded.data.map((entry) => (
                <tr key={entry.id}>
                  <td style={{ whiteSpace: 'nowrap' }}>{dayOf(entry.at)}, {timeOf(entry.at)}</td>
                  <td>{entry.adminName}</td>
                  <td style={{ whiteSpace: 'nowrap' }}>{actions[entry.action] ?? entry.action}</td>
                  <td>{entry.subject}</td>
                  <td className="small" style={{ minWidth: 220 }}>{entry.note ?? ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}
    </div>
  )
}
