/**
 * The Spring Boot API's JSON shapes, mirrored from the backend's DTO records.
 * Keep these in step with backend/src/main/java/com/thriftbite/backend/**\/dto.
 * Money arrives as JSON numbers (BigDecimal) and times as ISO-8601 strings (Instant).
 */

export type Role = 'ADMIN' | 'OWNER' | 'CONSUMER'
export type ItemStatus = 'AVAILABLE' | 'SOLD_OUT' | 'EXPIRED' | 'REMOVED'
export type ReservationStatus = 'RESERVED' | 'COMPLETED' | 'CANCELLED' | 'EXPIRED'

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
  ownerId: string
  ownerName: string
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
  ownerName: string
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
  /** Only sent to the buyer; null for the owner. */
  claimCode: string | null
  pickupDeadline: string
  total: number
  buyerName: string
  ownerName: string
  createdAt: string
  completedAt: string | null
  items: ReservationLine[]
}

/** RFC 9457 problem details, the shape of every API error. */
export interface ProblemDetail {
  status: number
  title?: string
  detail?: string
}
