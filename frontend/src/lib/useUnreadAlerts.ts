import { useCallback } from 'react'
import { api } from '../api/client'
import type { LiveMessage } from './live'
import { useLiveReload } from './useLive'
import { useLoad } from './useLoad'

const alertsChanged = (message: LiveMessage) => message.type === 'alerts'
const NONE = { unread: 0 }

/**
 * How many alerts the buyer hasn't read, for the number on the nav. It follows the
 * live connection: a new alert, or reading one on another device, updates it.
 */
export function useUnreadAlerts(enabled: boolean): number {
  const loaded = useLoad(useCallback(() => (enabled ? api.alertCount() : Promise.resolve(NONE)), [enabled]))
  useLiveReload(loaded.reload, alertsChanged)
  return enabled ? loaded.data?.unread ?? 0 : 0
}
