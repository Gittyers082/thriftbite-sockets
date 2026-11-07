/**
 * The Spring Boot API's JSON shapes, mirrored from the backend's DTO records.
 * Keep these in step with backend/src/main/java/com/thriftbite/backend/**\/dto.
 * Money arrives as JSON numbers (BigDecimal) and times as ISO-8601 strings (Instant).
 */

export type Role = 'ADMIN' | 'OWNER' | 'CONSUMER'
export type ItemStatus = 'AVAILABLE' | 'SOLD_OUT' | 'EXPIRED' | 'REMOVED'
export type ReservationStatus = 'RESERVED' | 'COMPLETED' | 'CANCELLED' | 'EXPIRED'
export type Weekday = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY'

export interface UserResponse {
  id: string
  username: string
  role: Role
  approved: boolean
}

export interface LoginResponse {
  token: string
  expiresAt: string
  user: UserResponse
}

export interface RegisterRequest {
  username: string
  password: string
  accountType: 'OWNER' | 'CONSUMER'
  storeName?: string
}

export interface ItemResponse {
  id: string
  name: string
  description: string | null
  price: number
  originalPrice: number
  discountPercent: number
  quantity: number
  status: ItemStatus
  expiresAt: string
  pickupLocation: string
  pickupStart: string
  pickupEnd: string
  storeId: string
  storeName: string
  storeSlug: string
  version: number
}

export interface ItemRequest {
  name: string
  description?: string
  price: number
  originalPrice: number
  quantity: number
  expiresAt: string
  pickupLocation: string
  pickupStart: string
  pickupEnd: string
  /** Required when updating: the version you loaded. */
  version?: number
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface CartLine {
  itemId: string
  name: string
  price: number
  quantity: number
  lineTotal: number
  quantityLeft: number
  available: boolean
  storeId: string
  storeName: string
  pickupLocation: string
}

export interface CartResponse {
  lines: CartLine[]
  total: number
}

export interface ReservationLine {
  itemId: string
  name: string
  unitPrice: number
  quantity: number
  pickupLocation: string
}

export interface ReservationResponse {
  id: string
  status: ReservationStatus
  /** Only sent to the buyer; null for the store. */
  claimCode: string | null
  pickupDeadline: string
  total: number
  buyerName: string
  storeId: string
  storeName: string
  createdAt: string
  completedAt: string | null
  cancelledAt: string | null
  expiredAt: string | null
  items: ReservationLine[]
}

export type DemoRole = 'BUYER' | 'OWNER' | 'ADMIN'

export interface DemoInfo {
  accounts: { as: DemoRole; username: string; description: string }[]
  seededAt: string
  resetsAt: string
}

export interface SalesStats {
  storeName: string
  from: string
  to: string
  timeZone: string
  totals: {
    moneyMade: number
    moneyMadePreviousPeriod: number
    moneyMadeChange: number | null
    unitsSold: number
    pickups: number
    discountGiven: number
    averageDiscount: number | null
    unitsListed: number
    unitsSoldOfListed: number
    unitsLeftOver: number
    sellThrough: number | null
    completedReservations: number
    missedPickups: number
    cancelledReservations: number
    missedRate: number | null
    missedValue: number
  }
  days: { date: string; weekday: Weekday; moneyMade: number; unitsSold: number; pickups: number }[]
  leftoverByWeekday: { weekday: Weekday; days: number; unitsLeftOver: number; averagePerDay: number | null }[]
  pickupsByHour: { hour: number; pickups: number }[]
  items: {
    name: string
    listed: number
    sold: number
    leftOver: number
    sellThrough: number | null
    moneyMade: number
    daysListed: number
    daysSoldOut: number
    /** "16:39:00", Manila time */
    usualSellOutTime: string | null
  }[]
}

/** Codes the API puts on errors the screens handle differently. */
export type ProblemCode = 'rate_limited' | 'login_locked' | 'claim_code_locked' | 'account_not_approved'

/** RFC 9457 problem details, the shape of every API error. */
export interface ProblemDetail {
  status: number
  title?: string
  detail?: string
  code?: ProblemCode
}
