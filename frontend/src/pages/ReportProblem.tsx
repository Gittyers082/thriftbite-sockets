import { useCallback, useState, type FormEvent } from 'react'
import { api } from '../api/client'
import type { Report, ReportKind } from '../api/types'
import { Empty, Loading, ProblemPanel } from '../components/States'
import { messageOf } from '../lib/errors'
import { dayOf } from '../lib/format'
import { reportKindLabel, reportKinds } from '../lib/reports'
import { href, paths } from '../lib/route'
import { gap } from '../lib/style'
import { useLoad } from '../lib/useLoad'

const MAX_DETAILS = 1000

/** What the report is about, once it's been looked up. */
interface Subject {
  storeName: string
  /** e.g. "2 × Pandesal, reserved 12 Nov"; null when the report is about the store in general. */
  pickup: string | null
  /** The report already sent about this pickup (or still open about this store), if there is one. */
  sent: Report | null
}

interface Props {
  /** Give one of the two: a pickup of the buyer's own, or a store. */
  reservationId?: string
  storeSlug?: string
}

/** Tell the admins something went wrong with a pickup, or with a store. */
export function ReportProblem({ reservationId, storeSlug }: Props) {
  const back = reservationId
    ? { to: paths.reservations, label: 'Back to reservations' }
    : { to: paths.store(storeSlug ?? ''), label: 'Back to the store' }

  const loaded = useLoad(useCallback(async (): Promise<Subject | null> => {
    if (reservationId) {
      const [reservations, reports] = await Promise.all([api.myReservations(), api.myReports()])
      const r = reservations.find((entry) => entry.id === reservationId)
      if (!r) return null
      return {
        storeName: r.storeName,
        pickup: `${r.items.map((line) => `${line.quantity} × ${line.name}`).join(', ')}, reserved ${dayOf(r.createdAt)}`,
        sent: reports.find((report) => report.reservationId === reservationId) ?? null,
      }
    }
    const [page, reports] = await Promise.all([api.store(storeSlug ?? ''), api.myReports()])
    return {
      storeName: page.store.name,
      pickup: null,
      // one open report per store at a time: show that one instead of a form that would be refused
      sent: reports.find((report) => report.storeSlug === page.store.slug && report.reservationId == null && report.status === 'OPEN') ?? null,
    }
  }, [reservationId, storeSlug]))

  if (loaded.error && !loaded.data) return <ProblemPanel error={loaded.error} onRetry={loaded.reload} />
  if (loaded.loading && loaded.data === undefined) return <Loading />
  if (!loaded.data) {
    return (
      <Empty title="We can’t find that reservation" action={<a className="btn teal" href={href(paths.reservations)}>See your reservations</a>}>
        It may belong to a different account.
      </Empty>
    )
  }
  return <ReportForm subject={loaded.data} reservationId={reservationId} storeSlug={storeSlug} back={back} />
}

function ReportForm({ subject, reservationId, storeSlug, back }: Props & { subject: Subject; back: { to: string; label: string } }) {
  const [kind, setKind] = useState<ReportKind | null>(null)
  const [details, setDetails] = useState('')
  const [touched, setTouched] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [sent, setSent] = useState<Report | null>(subject.sent)

  const kindProblem = kind == null ? 'Choose what went wrong.' : null
  const detailsProblem = kind === 'OTHER' && details.trim().length < 10 ? 'Tell us what happened, in a sentence or two.' : null

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setTouched(true)
    setError(null)
    if (kind == null || detailsProblem) return
    setBusy(true)
    try {
      setSent(await api.report({ reservationId, storeSlug, kind, details: details.trim() || undefined }))
    } catch (err) {
      setError(messageOf(err))
    } finally {
      setBusy(false)
    }
  }

  const head = (
    <div className="stack" style={gap(6)}>
      <a className="small" href={href(back.to)} style={{ fontWeight: 600 }}>{back.label}</a>
      <h1 className="wide">Report a problem</h1>
      <p className="muted">
        With {subject.pickup ? <>your pickup at <strong>{subject.storeName}</strong> ({subject.pickup})</> : <strong>{subject.storeName}</strong>}.
      </p>
    </div>
  )

  if (sent) {
    return (
      <div className="stack" style={gap(24, { maxWidth: 640 })}>
        <div className="page-head">{head}</div>
        <div role="status" className="card notice good" style={{ flexWrap: 'wrap' }}>
          <div className="grow stack" style={gap(4)}>
            <strong style={{ fontSize: 18 }}>
              {sent.status === 'RESOLVED' ? 'We’ve answered this report.' : subject.sent ? 'You’ve already reported this.' : 'Report sent. Thank you.'}
            </strong>
            <p>“{reportKindLabel(sent.kind)}”{sent.details ? `: ${sent.details}` : ''}</p>
            {sent.reply
              ? <p><strong>Our reply:</strong> {sent.reply}</p>
              : <p>We’ll look into it. The answer will be on your account page.</p>}
          </div>
          <a className="btn" href={href(paths.account)}>See your reports</a>
        </div>
      </div>
    )
  }

  return (
    <div className="stack" style={gap(24, { maxWidth: 640 })}>
      <div className="page-head">{head}</div>
      <form className="card stack" style={gap(18, { padding: 24 })} onSubmit={submit} noValidate>
        <fieldset className="stack" style={gap(4, { border: 0, margin: 0, padding: 0 })} aria-describedby={touched && kindProblem ? 'kind-err' : undefined}>
          <legend style={{ fontWeight: 700, marginBottom: 8 }}>What went wrong?</legend>
          {reportKinds.map((entry) => (
            <label key={entry.kind} className="row choice" style={gap(10)}>
              <input type="radio" name="kind" value={entry.kind} checked={kind === entry.kind} onChange={() => setKind(entry.kind)}
                style={{ width: 20, height: 20, flex: 'none', accentColor: 'var(--ink)' }} />
              {entry.label}
            </label>
          ))}
          {touched && kindProblem && <span id="kind-err" role="alert" className="err">{kindProblem}</span>}
        </fieldset>

        {kind === 'UNSAFE_FOOD' && (
          <p className="panel cream" style={{ padding: '12px 14px' }}>
            If you feel sick, see a doctor first. Keep the food, or a photo of it, if you can.
          </p>
        )}

        <div className="field">
          <label htmlFor="details">What happened?{kind !== 'OTHER' && ' (optional)'}</label>
          <textarea id="details" className={`input${touched && detailsProblem ? ' bad' : ''}`} rows={4} maxLength={MAX_DETAILS}
            value={details} onChange={(e) => setDetails(e.target.value)}
            aria-invalid={touched && detailsProblem ? true : undefined} aria-describedby="details-hint"
            style={{ resize: 'vertical', padding: '10px 14px', height: 'auto' }} />
          {touched && detailsProblem
            ? <span id="details-hint" role="alert" className="err">{detailsProblem}</span>
            : <span id="details-hint" className="hint">When you went, and what you saw. Leave out phone numbers and other private details.</span>}
        </div>

        {error && <p role="alert" className="err">{error}</p>}
        <div className="wrap-row">
          <button type="submit" className="btn teal big" disabled={busy}>{busy ? 'Sending…' : 'Send report'}</button>
          <a className="btn big" href={href(back.to)}>Cancel</a>
        </div>
        <p className="hint">
          Reports go to ThriftBite’s admins. The store isn’t told who sent it. You can send up to 5 a day.
        </p>
      </form>
    </div>
  )
}
