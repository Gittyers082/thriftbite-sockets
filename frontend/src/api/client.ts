import type {
  AccountSummary, AlertList, BatchListingRequest, CartResponse, DemoInfo, DemoRole, FollowState, FollowedStore, ItemRequest, ItemResponse, LoginResponse, PageResponse,
  PhotoUpload, ProblemCode, ProblemDetail, RecoverResponse, RegisterRequest, RegisterResponse, Report, ReportRequest, ReservationResponse, SalesStats, StorePage, StoreProfile,
  StoreProfileRequest, UserResponse, UsualListing,
} from './types'

/** Where the API lives. Set VITE_API_URL on Vercel; locally it's the Spring Boot default. */
export const API_BASE = (import.meta.env.VITE_API_URL ?? 'http://localhost:8080').replace(/\/$/, '')
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
  // A form (a file upload) goes as it is: the browser writes its own Content-Type for it.
  const form = body instanceof FormData
  if (body !== undefined && !form) headers['Content-Type'] = 'application/json'

  let res: Response
  try {
    res = await fetch(API_BASE + path, { method, headers, body: body === undefined ? undefined : form ? body : JSON.stringify(body) })
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
  if (status === 413) return 'That file is too big.'
  if (status === 429) return 'Too many tries. Wait a few minutes and try again.'
  if (status >= 500) return 'Something went wrong on our side. Try again in a minute.'
  return 'That didn’t work. Try again.'
}

/** The address of a photo, for an <img>. "thumb" is the small one for lists. */
export function photoUrl(photoId: string, size: 'full' | 'thumb' = 'full'): string {
  return `${API_BASE}/api/photos/${photoId}${size === 'thumb' ? '?size=thumb' : ''}`
}

export const api = {
  register: (body: RegisterRequest) => request<RegisterResponse>('POST', '/api/auth/register', body),
  /** Forgot password. Without `newPassword` it only checks the code; with it, the password is changed and you're logged in. */
  recover: (username: string, recoveryCode: string, newPassword?: string) =>
    request<RecoverResponse>('POST', '/api/auth/recover', { username, recoveryCode, newPassword }),

  account: () => request<AccountSummary>('GET', '/api/account'),
  /** Logs out every other device; the answer is a fresh login for this one. */
  changePassword: (currentPassword: string, newPassword: string) =>
    request<LoginResponse>('POST', '/api/account/password', { currentPassword, newPassword }),
  newRecoveryCodes: (password: string) => request<{ codes: string[] }>('POST', '/api/account/recovery-codes', { password }),
  /** Everything kept about this account, as JSON. */
  exportData: () => request<unknown>('GET', '/api/account/export'),
  deleteAccount: (password: string) => request<void>('POST', '/api/account/delete', { password }),
  login: (username: string, password: string) =>
    request<LoginResponse>('POST', '/api/auth/login', { username, password }),
  me: () => request<UserResponse>('GET', '/api/auth/me'),

  demoInfo: () => request<DemoInfo>('GET', '/api/demo'),
  demoLogin: (as: DemoRole) => request<LoginResponse>('POST', '/api/demo/login', { as }),

  browse: (q = '', page = 0, size = 48) =>
    request<PageResponse<ItemResponse>>('GET', `/api/items?q=${encodeURIComponent(q)}&page=${page}&size=${size}`),
  item: (id: string) => request<ItemResponse>('GET', `/api/items/${id}`),
  store: (slug: string) => request<StorePage>('GET', `/api/stores/${encodeURIComponent(slug)}`),

  /** Follow a store, or change whether its alerts are on. Safe to repeat. */
  follow: (slug: string, alertsOn = true) =>
    request<FollowState>('PUT', `/api/stores/${encodeURIComponent(slug)}/follow`, { alertsOn }),
  unfollow: (slug: string) => request<void>('DELETE', `/api/stores/${encodeURIComponent(slug)}/follow`),
  follows: () => request<FollowedStore[]>('GET', '/api/follows'),
  alerts: () => request<AlertList>('GET', '/api/alerts'),
  alertCount: () => request<{ unread: number }>('GET', '/api/alerts/count'),
  readAlert: (id: string) => request<void>('POST', `/api/alerts/${id}/read`),
  readAllAlerts: () => request<void>('POST', '/api/alerts/read'),

  cart: () => request<CartResponse>('GET', '/api/cart'),
  setCartQuantity: (itemId: string, quantity: number) =>
    request<CartResponse>('PUT', `/api/cart/items/${itemId}`, { quantity }),
  checkout: () => request<ReservationResponse[]>('POST', '/api/checkout'),
  myReservations: () => request<ReservationResponse[]>('GET', '/api/reservations'),
  cancel: (id: string) => request<ReservationResponse>('POST', `/api/reservations/${id}/cancel`),
  report: (body: ReportRequest) => request<Report>('POST', '/api/reports', body),
  myReports: () => request<Report[]>('GET', '/api/reports'),

  myItems: () => request<ItemResponse[]>('GET', '/api/owner/items'),
  createItem: (body: ItemRequest) => request<ItemResponse>('POST', '/api/owner/items', body),
  updateItem: (id: string, body: ItemRequest) => request<ItemResponse>('PUT', `/api/owner/items/${id}`, body),
  removeItem: (id: string) => request<void>('DELETE', `/api/owner/items/${id}`),
  /** What the store usually lists. `day` is the Manila date being posted for, today or tomorrow. */
  usualListings: (day: string) => request<UsualListing[]>('GET', `/api/owner/items/usual?for=${day}`),
  /** Several listings at once: either all are posted or none. */
  postBatch: (body: BatchListingRequest) => request<ItemResponse[]>('POST', '/api/owner/items/batch', body),
  /** Adds a photo to the store. Put the id it returns on a listing or on the store profile. */
  uploadPhoto: (photo: Blob) => {
    const form = new FormData()
    form.append('file', photo, 'photo.jpg')
    return request<PhotoUpload>('POST', '/api/owner/photos', form)
  },
  myStore: () => request<StoreProfile>('GET', '/api/owner/store'),
  saveStore: (body: StoreProfileRequest) => request<StoreProfile>('PUT', '/api/owner/store', body),
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
