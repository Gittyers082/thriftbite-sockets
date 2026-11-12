import { useCallback, useState } from 'react'
import { api } from '../api/client'
import type { CartResponse, ItemResponse } from '../api/types'
import { useAuth } from './auth-context'
import { messageOf } from './errors'
import { navigate, paths } from './route'
import { useLoad } from './useLoad'

/** The buyer's cart for screens that show deals: what's in it, and adding one more. */
export function useCart() {
  const { user } = useAuth()
  const isBuyer = user?.role === 'CONSUMER'
  const [changedCart, setChangedCart] = useState<CartResponse | null>(null)
  const [adding, setAdding] = useState<string | null>(null)
  const [problem, setProblem] = useState<string | null>(null)
  const loaded = useLoad(useCallback(() => (isBuyer ? api.cart() : Promise.resolve(null)), [isBuyer]))
  const cart = changedCart ?? loaded.data ?? null
  const inCart = new Map(cart?.lines.map((line) => [line.itemId, line.quantity]) ?? [])

  const add = async (item: ItemResponse) => {
    if (!user) return navigate(paths.login)
    setAdding(item.id)
    setProblem(null)
    try {
      setChangedCart(await api.setCartQuantity(item.id, (inCart.get(item.id) ?? 0) + 1))
    } catch (error) {
      setProblem(messageOf(error))
    } finally {
      setAdding(null)
    }
  }

  return {
    loggedIn: user !== null,
    /** Stores and admins can look, but only buyers reserve. */
    canReserve: user === null || isBuyer,
    cart,
    inCart,
    /** The id of the listing being added right now, if any. */
    adding,
    problem,
    add,
  }
}
