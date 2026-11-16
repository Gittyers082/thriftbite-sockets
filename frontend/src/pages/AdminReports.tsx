import { useCallback, useState, type FormEvent } from 'react'
import { api } from '../api/client'
import type { AdminReport } from '../api/types'
import { Empty, Loading, ProblemPanel } from '../components/States'
import { useAuth } from '../lib/auth-context'
import { messageOf } from '../lib/errors'
import { dayOf, plural } from '../lib/format'
import type { LiveMessage } from '../lib/live'
import { reportKindLabel } from '../lib/reports'
import { href, paths } from '../lib/route'
import { gap } from '../lib/style'
import { useLiveReload } from '../lib/useLive'
import { useLoad } from '../lib/useLoad'

/** A buyer sent a report, or another admin answered one. */
const reportsChanged = (message: LiveMessage) => message.type === 'reports'

/** Problems buyers reported. An admin reads each one, replies to the buyer, and that closes it. */
export function AdminReports() {
  const { user } = useAuth()
  const isDemo = user?.username === 'demo.admin'
  const [tab, setTab] = useState<'open' | 'resolved'>('open')
  const loaded = useLoad(useCallback(() => api.adminReports(tab), [tab]))
  useLiveReload(loaded.reload, reportsChanged)

  return (
    <div className="stack" style={gap(24, { maxWidth: 820 })}>
      <div className="page-head">
        <div className="stack" style={gap(6)}>
          <h1 className="wide">Reports</h1>
          <p className="muted">Problems buyers reported with a pickup or a store.</p>
        </div>
        <div role="tablist" aria-label="Show" className="segmented">
          <button type="button" role="tab" className="chip" aria-selected={tab === 'open'} onClick={() => setTab('open')}>Waiting</button>
          <button type="button" role="tab" className="chip" aria-selected={tab === 'resolved'} onClick={() => setTab('resolved')}>Answered</button>
        </div>
      </div>
      {isDemo && (
        <p className="panel notice dark">
          You’re the demo admin, so you only see reports the demo’s sample buyers made about its sample stores.
        </p>
      )}
      {tab === 'open' && (
        <p className="panel cream" style={{ padding: '16px 20px' }}>
          A report is one side of the story. Before you answer, look at the store’s page and how many reports it has had, and
          call the store if it’s about food safety. Your reply goes to the buyer; the store isn’t told who reported it.
        </p>
      )}

      {loaded.error && !loaded.data ? <ProblemPanel error={loaded.error} onRetry={loaded.reload} />
        : !loaded.data ? <Loading label="Loading reports…" />
        : loaded.data.length === 0 ? (
          tab === 'open'
            ? <Empty title="Nothing waiting">New reports show up here as soon as a buyer sends one.</Empty>
            : <Empty title="No answered reports yet">Reports you’ve answered are kept here.</Empty>
        ) : (
          <div className="stack" style={gap(16)}>
            {loaded.data.map((report) => <ReportCard key={report.id} report={report} onAnswered={loaded.reload} />)}
          </div>
        )}
    </div>
  )
}

function ReportCard({ report, onAnswered }: { report: AdminReport; onAnswered: () => void }) {
  const [reply, setReply] = useState('')
  const [excuse, setExcuse] = useState(false)
  const [touched, setTouched] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const open = report.status === 'OPEN'
  const tooShort = reply.trim().length < 10
  const id = `reply-${report.id}`

  const send = async (e: FormEvent) => {
    e.preventDefault()
    setTouched(true)
    setError(null)
    if (tooShort) return
    setBusy(true)
    try {
      await api.resolveReport(report.id, reply.trim(), excuse)
      onAnswered()
    } catch (err) {
      setError(messageOf(err))
      setBusy(false)
    }
  }

  return (
    <section className="card stack" style={gap(12, { padding: 20 })} aria-label={`${reportKindLabel(report.kind)}, ${report.storeName}`}>
      <div className="spread" style={{ flexWrap: 'wrap', gap: 8 }}>
        <h2 style={{ fontSize: 18 }}>{reportKindLabel(report.kind)}</h2>
        <span className="muted small">Sent {dayOf(report.createdAt)}</span>
      </div>
      <p>
        About <a href={href(paths.store(report.storeSlug))}><strong>{report.storeName}</strong></a>, reported by <strong>{report.reporter}</strong>.
      </p>
      <p className="small">
        {report.pickup ? <><strong>Pickup:</strong> {report.pickup}.</> : 'About the store in general, not one pickup.'}
      </p>
      {report.details
        ? <p className="panel cream" style={{ padding: '10px 14px' }}>“{report.details}”</p>
        : <p className="hint">{report.reporter === 'Deleted account' ? 'The buyer deleted their account, so what they wrote is gone.' : 'The buyer didn’t add any details.'}</p>}
      {(report.storeReports > 1 || report.reporterReports > 3) && (
        <div className="wrap-row" style={{ ['--gap' as string]: '8px' }}>
          {report.storeReports > 1 && <span className="tag orange">{plural(report.storeReports, 'report')} about this store in 90 days</span>}
          {report.reporterReports > 3 && <span className="tag cream">This buyer sent {plural(report.reporterReports, 'report')} in 90 days</span>}
        </div>
      )}

      {open ? (
        <form className="stack" style={gap(10)} onSubmit={send} noValidate>
          <div className="field">
            <label htmlFor={id}>Reply to the buyer</label>
            <textarea id={id} className={`input${touched && tooShort ? ' bad' : ''}`} rows={3} maxLength={500} value={reply}
              onChange={(e) => setReply(e.target.value)} aria-invalid={touched && tooShort ? true : undefined} aria-describedby={`${id}-hint`}
              style={{ resize: 'vertical', padding: '10px 14px', height: 'auto' }} />
            {touched && tooShort
              ? <span id={`${id}-hint`} role="alert" className="err">Write a sentence saying what you found or did.</span>
              : <span id={`${id}-hint`} className="hint">Say what you found and what you did about it. They’ll see it on their account page.</span>}
          </div>
          {report.pickupMissed && (
            <label className="row" style={gap(10, { cursor: 'pointer' })}>
              <input type="checkbox" checked={excuse} onChange={(e) => setExcuse(e.target.checked)}
                style={{ width: 20, height: 20, flex: 'none', accentColor: 'var(--ink)' }} />
              <span>Don’t count this missed pickup against the buyer <span className="hint">(for when it wasn’t their fault)</span></span>
            </label>
          )}
          {error && <p role="alert" className="err">{error}</p>}
          <div><button type="submit" className="btn teal" disabled={busy}>{busy ? 'Sending…' : 'Send reply and close'}</button></div>
        </form>
      ) : (
        <div className="stack" style={gap(4)}>
          <p><strong>Reply:</strong> {report.reply}</p>
          <span className="muted small">
            Answered{report.resolvedBy ? ` by ${report.resolvedBy}` : ''}{report.resolvedAt ? ` on ${dayOf(report.resolvedAt)}` : ''}
          </span>
        </div>
      )}
    </section>
  )
}
