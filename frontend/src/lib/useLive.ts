import { useEffect, useSyncExternalStore } from 'react'
import { liveStatus, subscribeLive, subscribeLiveStatus, type LiveMessage, type LiveStatus } from './live'

/** Calls `onMessage` for every live message while the screen is open. Wrap `onMessage` in useCallback. */
export function useLive(onMessage: (message: LiveMessage) => void): void {
  useEffect(() => subscribeLive(onMessage), [onMessage])
}

/**
 * Reloads when a message that `matters` arrives, or when the connection comes
 * back after a gap. Several messages in a row cause one reload.
 * `matters` must stay the same function between renders (define it outside the
 * component, or wrap it in useCallback).
 */
export function useLiveReload(reload: () => void, matters: (message: LiveMessage) => boolean): void {
  useEffect(() => {
    let timer: number | undefined
    const stop = subscribeLive((message) => {
      if (message.type !== 'reconnected' && !matters(message)) return
      window.clearTimeout(timer)
      timer = window.setTimeout(reload, 250)
    })
    return () => {
      window.clearTimeout(timer)
      stop()
    }
  }, [reload, matters])
}

export function useLiveStatus(): LiveStatus {
  return useSyncExternalStore(subscribeLiveStatus, liveStatus)
}
