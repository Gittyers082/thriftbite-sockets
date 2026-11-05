import { useEffect, useState } from 'react'

/**
 * A tiny hash router: "#/cart" -> "/cart". Hash routes work on any static host
 * (Vercel included) with no server rewrites. Swap for React Router once the
 * app grows past a handful of screens.
 */
export const routes = {
  browse: '/',
  cart: '/cart',
  reservations: '/reservations',
  pickup: '/pickup',
  login: '/login',
  owner: '/owner',
  newListing: '/owner/new',
  admin: '/admin',
} as const

export type Route = (typeof routes)[keyof typeof routes]

export function href(route: Route): string {
  return '#' + route
}

function current(): string {
  const path = window.location.hash.replace(/^#/, '')
  return path === '' ? '/' : path
}

export function useRoute(): string {
  const [path, setPath] = useState(current)
  useEffect(() => {
    const onChange = () => {
      setPath(current())
      window.scrollTo(0, 0)
    }
    window.addEventListener('hashchange', onChange)
    return () => window.removeEventListener('hashchange', onChange)
  }, [])
  return path
}

export function navigate(route: Route): void {
  window.location.hash = route
}
