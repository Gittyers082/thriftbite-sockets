import { useCallback } from 'react'
import { api } from '../api/client'
import type { AdminSummary } from '../api/types'
import type { LiveMessage } from './live'
import { useLiveReload } from './useLive'
import { useLoad } from './useLoad'

const workChanged = (message: LiveMessage) => message.type === 'approvals' || message.type === 'reports'
const NONE: AdminSummary = { pendingStores: 0, openReports: 0 }

/**
 * How much is waiting for an admin, for the numbers on the nav. It follows the live
 * connection: a new sign-up or report, or another admin dealing with one, updates it.
 */
export function useAdminSummary(enabled: boolean): AdminSummary {
  const loaded = useLoad(useCallback(() => (enabled ? api.adminSummary() : Promise.resolve(NONE)), [enabled]))
  useLiveReload(loaded.reload, workChanged)
  return enabled ? loaded.data ?? NONE : NONE
}
