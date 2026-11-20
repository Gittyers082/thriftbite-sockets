import { useCallback, useState } from 'react'
import type { ItemResponse } from '../api/types'
import type { LiveMessage } from './live'
import { useLive, useLiveReload } from './useLive'

/**
 * Keeps the listings on screen in step with live stock messages.
 *
 * A listing that's on screen is changed in place. One that isn't (new, or freed
 * by a cancellation) can't be patched in, so the list is loaded again; `belongs`
 * can narrow that to listings this screen would show.
 *
 * @param shown  the listings on screen now
 * @param patch  applies a change to them (wrap in useCallback)
 * @param reload loads the list again
 * @returns how many live changes each listing has had, so its count can flash
 */
export function useLiveStock(
  shown: ItemResponse[] | undefined,
  patch: (change: (items: ItemResponse[]) => ItemResponse[]) => void,
  reload: () => void,
): Record<string, number> {
  const [changes, setChanges] = useState<Record<string, number>>({})

  useLive(useCallback((message: LiveMessage) => {
    if (message.type !== 'item' || !shown?.some((item) => item.id === message.id)) return
    patch((items) => items.map((item) =>
      item.id === message.id ? { ...item, quantity: message.quantity, status: message.status } : item))
    setChanges((counts) => ({ ...counts, [message.id]: (counts[message.id] ?? 0) + 1 }))
  }, [shown, patch]))

  useLiveReload(reload, useCallback((message: LiveMessage) =>
    message.type === 'deals'
    || (message.type === 'item' && message.status === 'AVAILABLE' && !shown?.some((item) => item.id === message.id)),
  [shown]))

  return changes
}
