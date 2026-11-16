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

/** A new account, with its recovery codes. They are shown once and can't be read again. */
export interface RegisterResponse extends UserResponse {
  recoveryCodes: string[]
}

/** After a password reset. With only a code checked (no new password yet), token and user are null. */
export interface RecoverResponse {
  token: string | null
  expiresAt: string | null
  user: UserResponse | null
  recoveryCodesLeft: number
}

/** The account page. */
export interface AccountSummary {
  username: string
  role: Role
  memberSince: string
  /** A shared demo account: it can't change its sign-in settings. */
  demo: boolean
  recoveryCodesLeft: number
  recoveryCodesMadeAt: string | null
  /** Null for store and admin accounts. */
  buyer: {
    saved: number
    mealsSaved: number
    /** The last three calendar months, oldest first. `month` is "2026-11". */
    months: { month: string; saved: number }[]
    /** Not picked up in the last 30 days, newest first. `day` is "2026-11-04". */
    missedPickups: { storeName: string; day: string }[]
    activeReservations: number
    maxActiveReservations: number
  } | null
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
  /** Show it with photoUrl(); null if the listing has no photo. */
  photoId: string | null
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
  /** A photo from api.uploadPhoto(). Leaving it out means "no photo", also when updating. */
  photoId?: string | null
}

/** What uploading a photo gives back. The size is the stored photo's, in pixels. */
export interface PhotoUpload {
  id: string
  width: number
  height: number
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

/** One day the store is open. Times are Manila time, "07:00:00". */
export interface StoreHours {
  weekday: Weekday
  opens: string
  closes: string
}

/** A store as buyers and its owner see it. */
export interface StoreProfile {
  id: string
  slug: string
  name: string
  address: string | null
  barangay: string | null
  landmark: string | null
  gcashAccepted: boolean
  /** The pickup window new listings start with, Manila time; null until the owner sets it. */
  defaultPickupStart: string | null
  defaultPickupEnd: string | null
  /** Open days only, Monday first. A day that's missing is closed. */
  hours: StoreHours[]
  /** A photo of the storefront, or null. */
  photoId: string | null
  version: number
}

export interface StoreProfileRequest {
  name: string
  address: string | null
  barangay: string | null
  landmark: string | null
  gcashAccepted: boolean
  defaultPickupStart: string | null
  defaultPickupEnd: string | null
  hours: StoreHours[]
  version: number
  photoId: string | null
}

/** A store's public page. */
export interface StorePage {
  store: StoreProfile
  /** On sale or just sold out, not yet expired. */
  items: ItemResponse[]
  /** Counted over the last 30 days (followers: right now). The nulls mean "not enough history to say". */
  stats: { mealsRescued: number; pickupRate: number | null; usualPostTime: string | null; followers: number }
  /** How the buyer looking at the page relates to the store. Null for visitors, stores and admins. */
  viewer: FollowState | null
}

export interface FollowState {
  following: boolean
  /** False means following quietly: the store is on your list but doesn't alert you. */
  alertsOn: boolean
}

/** A store the buyer follows. */
export interface FollowedStore {
  storeId: string
  slug: string
  name: string
  /** The barangay, or the street address if the store hasn't given one. */
  area: string | null
  /** "15:30:00" Manila time; null until the store has posted a few times. */
  usualPostTime: string | null
  alertsOn: boolean
}

export type AlertKind = 'DEAL' | 'PICKUP' | 'PICKED_UP' | 'MISSED' | 'REPORT'

export interface AlertItem {
  id: string
  kind: AlertKind
  title: string
  body: string
  /** A page in the app, e.g. "/stores/sunrise-bakery". */
  link: string
  createdAt: string
  read: boolean
}

export interface AlertList {
  unread: number
  alerts: AlertItem[]
}

/** Something the store has listed before, and how it went last time. */
export interface UsualListing {
  name: string
  description: string | null
  price: number
  originalPrice: number
  pickupLocation: string
  /** "2026-11-03" */
  lastDay: string
  sameWeekday: boolean
  lastListed: number
  lastSold: number
  lastSoldOutAt: string | null
  /** A different amount worth trying, or null when last time's amount looks right. */
  suggestedQuantity: number | null
  /** The photo it last had, so posting again keeps it. */
  photoId: string | null
  alreadyListed: boolean
}

export interface BatchListingRequest {
  pickupStart: string
  pickupEnd: string
  items: {
    name: string; description?: string; price: number; originalPrice: number; quantity: number; pickupLocation: string
    photoId?: string | null
  }[]
}

export type ReportKind = 'STORE_CLOSED' | 'NO_FOOD' | 'NOT_AS_LISTED' | 'UNSAFE_FOOD' | 'OVERCHARGED' | 'OTHER'

/** "Report a problem": about one of your pickups, or about a store. Give exactly one of the two. */
export interface ReportRequest {
  reservationId?: string
  storeSlug?: string
  kind: ReportKind
  details?: string
}

/** A report as the buyer who sent it sees it. */
export interface Report {
  id: string
  kind: ReportKind
  storeName: string
  storeSlug: string
  /** Null when the report is about the store in general. */
  reservationId: string | null
  details: string | null
  status: 'OPEN' | 'RESOLVED'
  /** What the admin said when closing it, if anything. */
  reply: string | null
  createdAt: string
  resolvedAt: string | null
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
export type ProblemCode =
  | 'rate_limited' | 'login_locked' | 'claim_code_locked' | 'account_not_approved' | 'recovery_locked' | 'password_locked'
  | 'report_limit' | 'photos_busy'

/** RFC 9457 problem details, the shape of every API error. */
export interface ProblemDetail {
  status: number
  title?: string
  detail?: string
  code?: ProblemCode
}

/** A problem report as an admin sees it. */
export interface AdminReport {
  id: string
  kind: ReportKind
  details: string | null
  status: 'OPEN' | 'RESOLVED'
  reply: string | null
  createdAt: string
  resolvedAt: string | null
  /** The admin who answered it. */
  resolvedBy: string | null
  /** The buyer's username, or "Deleted account". */
  reporter: string
  storeName: string
  storeSlug: string
  /** e.g. "2 × Pandesal, reserved 12 Nov, picked up"; null when it's about the store in general. */
  pickup: string | null
  /** Reports about this store, and sent by this buyer, in the last 90 days (this one included). */
  storeReports: number
  reporterReports: number
}

/** How much is waiting for an admin. */
export interface AdminSummary {
  pendingStores: number
  openReports: number
}

/** One line of the record of what admins did. */
export interface AdminLogEntry {
  id: string
  adminName: string
  action: 'APPROVE_STORE' | 'ANSWER_REPORT' | string
  /** Who or what it was done to. */
  subject: string
  /** The admin's own words, where there are any. */
  note: string | null
  at: string
}
