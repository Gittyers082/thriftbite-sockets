import type {
  CartResponse, ItemRequest, ItemResponse, LoginResponse, PageResponse,
  ProblemDetail, RegisterRequest, ReservationResponse, UserResponse,
} from './types'

/**
 * A small typed client for the Spring Boot API. Not used by the draft screens
 * yet: they run on src/data/sample.ts. Set VITE_API_URL to point it elsewhere
 * (defaults to the local backend).
 */
const BASE = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'
const TOKEN_KEY = 'thriftbite.token'

export class ApiError extends Error {
  readonly status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

export function getToken(): string | null {
  try { return localStorage.getItem(TOKEN_KEY) } catch { return null }
}

export function setToken(token: string | null): void {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token)
    else localStorage.removeItem(TOKEN_KEY)
  } catch { /* storage unavailable: stay logged out */ }
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {}
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  const res = await fetch(BASE + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
  if (!res.ok) {
    let message = res.statusText
    try { message = ((await res.json()) as ProblemDetail).detail ?? message } catch { /* not JSON */ }
    throw new ApiError(res.status, message)
  }
  return (res.status === 204 ? undefined : await res.json()) as T
}

export const api = {
  register: (body: RegisterRequest) => request<UserResponse>('POST', '/api/auth/register', body),
  login: async (username: string, password: string) => {
    const res = await request<LoginResponse>('POST', '/api/auth/login', { username, password })
    setToken(res.token)
    return res
  },
  me: () => request<UserResponse>('GET', '/api/auth/me'),

  browse: (q = '', page = 0, size = 20) =>
    request<PageResponse<ItemResponse>>('GET', `/api/items?q=${encodeURIComponent(q)}&page=${page}&size=${size}`),
  item: (id: string) => request<ItemResponse>('GET', `/api/items/${id}`),

  cart: () => request<CartResponse>('GET', '/api/cart'),
  setCartQuantity: (itemId: string, quantity: number) => request<CartResponse>('PUT', `/api/cart/items/${itemId}`, { quantity }),
  checkout: () => request<ReservationResponse[]>('POST', '/api/checkout'),
  myReservations: () => request<ReservationResponse[]>('GET', '/api/reservations'),
  cancel: (id: string) => request<ReservationResponse>('POST', `/api/reservations/${id}/cancel`),

  myItems: () => request<ItemResponse[]>('GET', '/api/owner/items'),
  createItem: (body: ItemRequest) => request<ItemResponse>('POST', '/api/owner/items', body),
  updateItem: (id: string, body: ItemRequest) => request<ItemResponse>('PUT', `/api/owner/items/${id}`, body),
  removeItem: (id: string) => request<void>('DELETE', `/api/owner/items/${id}`),
  ownerPickups: () => request<ReservationResponse[]>('GET', '/api/owner/reservations'),
  completePickup: (id: string, claimCode: string) =>
    request<ReservationResponse>('POST', `/api/owner/reservations/${id}/complete`, { claimCode }),

  pendingOwners: () => request<UserResponse[]>('GET', '/api/admin/owners/pending'),
  approve: (userId: string) => request<UserResponse>('POST', `/api/admin/users/${userId}/approve`),
}
