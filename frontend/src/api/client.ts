import type {
  CartResponse, DemoInfo, DemoRole, ItemRequest, ItemResponse, LoginResponse, PageResponse,
  ProblemCode, ProblemDetail, RegisterRequest, ReservationResponse, SalesStats, UserResponse,
} from './types'

/** Where the API lives. Set VITE_API_URL on Vercel; locally it's the Spring Boot default. */
const BASE = (import.meta.env.VITE_API_URL ?? 'http://localhost:8080').replace(/\/$/, '')
const TOKEN_KEY = 'thriftbite.token'

/** The API answered with an error (4xx/5xx). `detail` is written for people and safe to show. */
export class ApiError extends Error {
  readonly status: number
  readonly code?: ProblemCode
  /** Seconds, from the Retry-After header on 429s. */
  readonly retryAfter?: number

  constructor(status: number, message: string, code?: ProblemCode, retryAfter?: number) {
    super(message)
    this.status = status
    this.code = code
    this.retryAfter = retryAfter
  }
}

/** The request never got an answer: no connection, the server is asleep or down, or CORS refused it. */
export class NetworkError extends Error {
  constructor() {
    super("Can't reach ThriftBite. Check your connection and try again.")
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

/** Called when the API says the token is no longer good, so the app can log out. */
let onUnauthorized: () => void = () => {}
export function setUnauthorizedHandler(handler: () => void): void {
  onUnauthorized = handler
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  let res: Response
  try {
    res = await fetch(BASE + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
  } catch {
    throw new NetworkError()
  }

  if (!res.ok) {
    let problem: Partial<ProblemDetail> = {}
    try { problem = (await res.json()) as ProblemDetail } catch { /* not JSON */ }
    if (res.status === 401 && token) onUnauthorized()
    const retry = Number(res.headers.get('Retry-After'))
    throw new ApiError(res.status, problem.detail ?? defaultMessage(res.status), problem.code,
      Number.isFinite(retry) && retry > 0 ? retry : undefined)
  }
  return (res.status === 204 ? undefined : await res.json()) as T
}

function defaultMessage(status: number): string {
  if (status === 401) return 'Please log in again.'
  if (status === 403) return "This account can't do that."
  if (status === 404) return "That isn't here anymore."
  if (status === 429) return 'Too many tries. Wait a few minutes and try again.'
  if (status >= 500) return 'Something went wrong on our side. Try again in a minute.'
  return 'That didn’t work. Try again.'
}

export const api = {
  register: (body: RegisterRequest) => request<UserResponse>('POST', '/api/auth/register', body),
  login: (username: string, password: string) =>
    request<LoginResponse>('POST', '/api/auth/login', { username, password }),
  me: () => request<UserResponse>('GET', '/api/auth/me'),

  demoInfo: () => request<DemoInfo>('GET', '/api/demo'),
  demoLogin: (as: DemoRole) => request<LoginResponse>('POST', '/api/demo/login', { as }),

  browse: (q = '', page = 0, size = 48) =>
    request<PageResponse<ItemResponse>>('GET', `/api/items?q=${encodeURIComponent(q)}&page=${page}&size=${size}`),
  item: (id: string) => request<ItemResponse>('GET', `/api/items/${id}`),

  cart: () => request<CartResponse>('GET', '/api/cart'),
  setCartQuantity: (itemId: string, quantity: number) =>
    request<CartResponse>('PUT', `/api/cart/items/${itemId}`, { quantity }),
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
  salesStats: (from?: string, to?: string) => {
    const query = new URLSearchParams()
    if (from) query.set('from', from)
    if (to) query.set('to', to)
    const qs = query.toString()
    return request<SalesStats>('GET', '/api/owner/stats' + (qs ? '?' + qs : ''))
  },

  pendingOwners: () => request<UserResponse[]>('GET', '/api/admin/owners/pending'),
  approve: (userId: string) => request<UserResponse>('POST', `/api/admin/users/${userId}/approve`),
}
