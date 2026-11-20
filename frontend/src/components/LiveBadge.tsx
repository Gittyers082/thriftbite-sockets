import { useLiveStatus } from '../lib/useLive'

/** Says whether this screen is updating on its own right now. Shows nothing while first connecting. */
export function LiveBadge({ what }: { what: string }) {
  const status = useLiveStatus()
  if (status === 'live') {
    return (
      <span className="tag lime live-badge" title={`${what} update on their own. No need to refresh.`}>
        <span className="live-dot" aria-hidden="true" />Live
      </span>
    )
  }
  if (status === 'reconnecting') {
    return <span className="tag" role="status">Reconnecting…</span>
  }
  return null
}
