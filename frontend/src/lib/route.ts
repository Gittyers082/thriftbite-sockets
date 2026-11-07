import { useEffect, useState } from 'react'

/**
 * A tiny hash router: "#/cart" -> "/cart". Hash routes work on any static
 * host (Vercel included) with no server rewrites.
 */
export const paths = {
  browse: '/',
  cart: '/cart',
  reservations: '/reservations',
  pickup: (reservationId: string) => `/pickup/${reservationId}`,
  login: '/login',
  signup: '/signup',
  pending: '/pending',
  owner: '/owner',
  newListing: '/owner/new',
  sales: '/owner/sales',
  admin: '/admin',
  privacy: '/privacy',
} as const

export function href(path: string): string {
  return '#' + path
}

function current(): string {
  const path = window.location.hash.replace(/^#/, '').split('?')[0]
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

export function navigate(path: string): void {
  window.location.hash = path
}

/** "/pickup/abc" against "/pickup/:id" -> { id: "abc" }, or null if it doesn't fit. */
export function match(path: string, pattern: string): Record<string, string> | null {
  const a = path.split('/')
  const b = pattern.split('/')
  if (a.length !== b.length) return null
  const params: Record<string, string> = {}
  for (let i = 0; i < b.length; i++) {
    if (b[i].startsWith(':')) params[b[i].slice(1)] = decodeURIComponent(a[i])
    else if (a[i] !== b[i]) return null
  }
  return params
}
