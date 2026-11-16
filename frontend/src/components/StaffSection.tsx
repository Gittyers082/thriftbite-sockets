import { useCallback, useState, type FormEvent } from 'react'
import { api } from '../api/client'
import type { NewStaff, StaffMember } from '../api/types'
import { messageOf } from '../lib/errors'
import { dayOf } from '../lib/format'
import { gap } from '../lib/style'
import { useLoad } from '../lib/useLoad'

const MAX_STAFF = 5

/**
 * The owner's staff list, on the store profile page: add an account for someone who works
 * the counter, give them a new password, or remove them.
 */
export function StaffSection() {
  const loaded = useLoad(useCallback(() => api.staff(), []))
  const [username, setUsername] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  /** The password to pass on. It can't be shown again, so it stays until the owner dismisses it. */
  const [issued, setIssued] = useState<NewStaff | null>(null)
  const [removing, setRemoving] = useState<string | null>(null)

  const run = async (action: () => Promise<NewStaff | void>) => {
    setError(null)
    setBusy(true)
    try {
      const result = await action()
      setIssued(result ?? null)
      setRemoving(null)
      loaded.reload()
      return true
    } catch (err) {
      setError(messageOf(err))
      return false
    } finally {
      setBusy(false)
    }
  }
  const add = async (e: FormEvent) => {
    e.preventDefault()
    if (await run(() => api.addStaff(username.trim()))) setUsername('')
  }

  const staff = loaded.data ?? []
  return (
    <section className="card" id="staff" style={{ maxWidth: 820 }}>
      <div className="card-head"><h2>Staff</h2></div>
      <div className="card-row stack" style={gap(8)}>
        <p>
          Give the people who work your counter their own login. Staff can list food and hand over pickups. They can’t
          see your sales, change this profile, or manage staff.
        </p>
      </div>

      {issued && (
        <div role="status" className="card-row stack" style={gap(8, { background: 'var(--lime)' })}>
          <strong>Give {issued.username} this password. It won’t be shown again.</strong>
          <code className="num" style={{ fontSize: 24, fontWeight: 700, letterSpacing: 1 }}>{issued.temporaryPassword}</code>
          <p className="small">They log in with it and can change it on their account page. If it gets lost, give them a new one from here.</p>
          <div><button type="button" className="btn sm" onClick={() => setIssued(null)}>I’ve passed it on</button></div>
        </div>
      )}

      {loaded.error != null && !loaded.data && <p role="alert" className="card-row err">{messageOf(loaded.error)}</p>}
      {staff.map((member) => (
        <StaffRow key={member.id} member={member} busy={busy} confirming={removing === member.id}
          onNewPassword={() => run(() => api.newStaffPassword(member.id))}
          onAskRemove={() => { setRemoving(member.id); setError(null) }}
          onCancelRemove={() => setRemoving(null)}
          onRemove={() => run(() => api.removeStaff(member.id))} />
      ))}
      {loaded.data && staff.length === 0 && <p className="card-row muted">No staff accounts yet.</p>}

      <form className="card-row stack" style={gap(10)} onSubmit={add}>
        <div className="wrap-row" style={{ ['--gap' as string]: '12px', alignItems: 'flex-end' }}>
          <div className="field grow" style={{ maxWidth: 320 }}>
            <label htmlFor="staff-username">Username for a new staff account</label>
            <input id="staff-username" className="input" autoComplete="off" autoCapitalize="none" spellCheck={false}
              required minLength={3} maxLength={30} pattern="[a-zA-Z0-9_.\-]+" value={username}
              onChange={(e) => setUsername(e.target.value)} aria-describedby="staff-hint" />
          </div>
          <button type="submit" className="btn teal" disabled={busy || staff.length >= MAX_STAFF}>{busy ? 'Working…' : 'Add staff account'}</button>
        </div>
        <span id="staff-hint" className="hint">
          {staff.length >= MAX_STAFF
            ? `A store can have ${MAX_STAFF} staff accounts. Remove one to add another.`
            : 'Letters, numbers, dots, dashes and underscores. We make the password; you pass it on.'}
        </span>
        {error && <p role="alert" className="err">{error}</p>}
      </form>
    </section>
  )
}

interface RowProps {
  member: StaffMember
  busy: boolean
  confirming: boolean
  onNewPassword: () => void
  onAskRemove: () => void
  onCancelRemove: () => void
  onRemove: () => void
}

function StaffRow({ member, busy, confirming, onNewPassword, onAskRemove, onCancelRemove, onRemove }: RowProps) {
  return (
    <div className="card-row stack" style={gap(10)}>
      <div className="spread" style={{ flexWrap: 'wrap', gap: 12 }}>
        <div className="row" style={gap(12)}>
          <span className="avatar" aria-hidden="true" style={{ background: 'var(--orange)' }}>{member.username.charAt(0)}</span>
          <div>
            <strong>{member.username}</strong>
            <p className="hint">Added {dayOf(member.addedAt)}</p>
          </div>
          {member.suspended && <span className="tag orange">Suspended by an admin</span>}
        </div>
        {!confirming && (
          <div className="row" style={gap(8)}>
            <button type="button" className="btn sm" disabled={busy} onClick={onNewPassword} aria-label={`New password for ${member.username}`}>New password</button>
            <button type="button" className="btn sm" disabled={busy} onClick={onAskRemove} aria-label={`Remove ${member.username}`}>Remove</button>
          </div>
        )}
      </div>
      {confirming && (
        <div className="panel stack" style={gap(10, { padding: 14, borderColor: 'var(--bad)' })}>
          <p><strong>Remove {member.username}?</strong> They’re logged out and can’t log in again. What they listed stays up.</p>
          <div className="wrap-row" style={{ ['--gap' as string]: '8px' }}>
            <button type="button" className="btn sm" onClick={onCancelRemove}>Keep the account</button>
            <button type="button" className="btn sm danger" disabled={busy} onClick={onRemove}>{busy ? 'Removing…' : 'Remove account'}</button>
          </div>
        </div>
      )}
    </div>
  )
}
