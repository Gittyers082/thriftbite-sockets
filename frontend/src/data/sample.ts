/**
 * Sample data for the draft UI. Every screen reads from here until it is wired
 * to the API (see src/api). Times are hours of the day: 16.5 = 4:30 PM.
 * Replace all of it with real data before launch.
 */

export const NOW = 16 + 40 / 60 // 4:40 PM

export type Kind = 'bakery' | 'meals' | 'snacks'

export interface Deal {
  id: string
  name: string
  kind: Kind
  store: string
  area: string
  pickupStart: number
  pickupEnd: number
  left: number
  price: number
  usual: number
}

export const deals: Deal[] = [
  { id: 'taho', name: 'Strawberry taho (2 cups)', kind: 'snacks', store: 'Burnham Taho Cart', area: 'Burnham Park', pickupStart: 16, pickupEnd: 18, left: 8, price: 20, usual: 35 },
  { id: 'pandesal', name: 'Pandesal (bag of 10)', kind: 'bakery', store: 'Sunrise Bakery', area: 'Session Rd', pickupStart: 17, pickupEnd: 19, left: 3, price: 25, usual: 50 },
  { id: 'ensaymada', name: 'Ube cheese ensaymada (box of 4)', kind: 'bakery', store: 'Sunrise Bakery', area: 'Session Rd', pickupStart: 17, pickupEnd: 19, left: 2, price: 90, usual: 150 },
  { id: 'spanish', name: 'Spanish bread (6 pcs)', kind: 'bakery', store: 'Sunrise Bakery', area: 'Session Rd', pickupStart: 17, pickupEnd: 19, left: 4, price: 40, usual: 70 },
  { id: 'adobo', name: 'Chicken adobo with rice', kind: 'meals', store: 'Lola Ising’s Carinderia', area: 'Bonifacio St', pickupStart: 18, pickupEnd: 20, left: 6, price: 60, usual: 95 },
  { id: 'pinakbet', name: 'Pinakbet family tray', kind: 'meals', store: 'Lola Ising’s Carinderia', area: 'Bonifacio St', pickupStart: 18, pickupEnd: 20, left: 2, price: 120, usual: 220 },
  { id: 'banana', name: 'Banana bread loaf', kind: 'bakery', store: 'Pine Grove Café', area: 'Legarda Rd', pickupStart: 18, pickupEnd: 20, left: 0, price: 70, usual: 140 },
  { id: 'lumpia', name: 'Lumpiang shanghai (10 pcs)', kind: 'meals', store: 'Kusina ni Ading', area: 'Abanao St', pickupStart: 18.5, pickupEnd: 20.5, left: 5, price: 55, usual: 90 },
]

/** Whole-day totals per tab; the list above is the first page. */
export const dealTotals: Record<Kind | 'all', number> = { all: 18, bakery: 7, meals: 6, snacks: 5 }

export const areas = ['Session Rd', 'Bonifacio St', 'Burnham Park', 'Legarda Rd', 'Abanao St']

export interface CartLine {
  dealId: string
  name: string
  store: string
  area: string
  window: string
  price: number
  usual: number
  qty: number
  left: number
  soldOut: boolean
}

export const cart: CartLine[] = [
  { dealId: 'pandesal', name: 'Pandesal (bag of 10)', store: 'Sunrise Bakery', area: 'Session Rd', window: '5:00–7:00 PM', price: 25, usual: 50, qty: 2, left: 3, soldOut: false },
  { dealId: 'ensaymada', name: 'Ube cheese ensaymada (box of 4)', store: 'Sunrise Bakery', area: 'Session Rd', window: '5:00–7:00 PM', price: 90, usual: 150, qty: 1, left: 2, soldOut: false },
  { dealId: 'banana', name: 'Banana bread loaf', store: 'Pine Grove Café', area: 'Legarda Rd', window: '6:00–8:00 PM', price: 70, usual: 140, qty: 1, left: 0, soldOut: true },
]

export type ReservationStatus = 'RESERVED' | 'COMPLETED' | 'CANCELLED' | 'EXPIRED'

export interface PastReservation {
  id: string
  date: string
  store: string
  food: string
  total: number
  status: Exclude<ReservationStatus, 'RESERVED'>
}

export const activeReservation = {
  id: 'r-482913',
  claimCode: '482913',
  store: 'Sunrise Bakery',
  area: 'Session Rd',
  window: '5:00–7:00 PM',
  pickupBy: '7:00 PM',
  lines: [
    { name: 'Pandesal (bag of 10)', qty: 2, total: 50 },
    { name: 'Ube cheese ensaymada (box of 4)', qty: 1, total: 90 },
  ],
  usualTotal: 250,
}

export const pastReservations: PastReservation[] = [
  { id: 'r-1', date: '2026-11-03', store: 'Lola Ising’s Carinderia', food: '1 × Chicken adobo with rice', total: 60, status: 'COMPLETED' },
  { id: 'r-2', date: '2026-11-01', store: 'Burnham Taho Cart', food: '2 × Strawberry taho (2 cups)', total: 40, status: 'CANCELLED' },
  { id: 'r-3', date: '2026-10-30', store: 'Pine Grove Café', food: '1 × Banana bread loaf', total: 70, status: 'EXPIRED' },
]

/* ---------- store owner: Sunrise Bakery ---------- */

export const STORE = { name: 'Sunrise Bakery', area: 'Session Rd', opens: 7, closes: 19 }

export type PickupState = 'waiting' | 'wrong' | 'done'

export interface Pickup {
  id: string
  buyer: string
  items: string
  total: number
  claimCode: string
  state: PickupState
  doneAt?: string
}

export const pickups: Pickup[] = [
  { id: 'p-steven', buyer: 'steven', items: '2 × Pandesal (bag of 10), 1 × Ube cheese ensaymada', total: 140, claimCode: '482913', state: 'waiting' },
  { id: 'p-mara', buyer: 'mara.l', items: '1 × Pandesal (bag of 10)', total: 25, claimCode: '730118', state: 'waiting' },
  { id: 'p-jun', buyer: 'jun.d', items: '2 × Cheese rolls (6 pcs)', total: 90, claimCode: '559020', state: 'done', doneAt: '4:20 PM' },
]

export type ListingStatus = 'AVAILABLE' | 'SOLD_OUT' | 'EXPIRED'

export interface Listing {
  id: string
  name: string
  price: number
  usual: number
  left: number
  listed: number
  expires: string
  status: ListingStatus
}

export const listings: Listing[] = [
  { id: 'l-1', name: 'Pandesal (bag of 10)', price: 25, usual: 50, left: 3, listed: 6, expires: 'Today 9:00 PM', status: 'AVAILABLE' },
  { id: 'l-2', name: 'Ube cheese ensaymada (box of 4)', price: 90, usual: 150, left: 2, listed: 3, expires: 'Today 9:00 PM', status: 'AVAILABLE' },
  { id: 'l-3', name: 'Spanish bread (6 pcs)', price: 40, usual: 70, left: 4, listed: 4, expires: 'Today 8:00 PM', status: 'AVAILABLE' },
  { id: 'l-4', name: 'Cheese rolls (6 pcs)', price: 45, usual: 90, left: 0, listed: 2, expires: 'Today 8:00 PM', status: 'SOLD_OUT' },
  { id: 'l-5', name: 'Monay (bag of 6)', price: 20, usual: 40, left: 2, listed: 4, expires: '2026-11-04 8:00 PM', status: 'EXPIRED' },
]

/** Lines that sold out today, against the same line last Sunday. */
export const sellOuts = [
  { name: 'Cheese rolls (6 pcs)', today: 16 + 5 / 60, lastSunday: 16 + 50 / 60 },
]

/* ---------- admin ---------- */

export interface PendingOwner {
  username: string
  signedUp: string
  when: string
}

export const pendingOwners: PendingOwner[] = [
  { username: 'sunrise.bakery', signedUp: '2026-11-03', when: '2 days ago' },
  { username: 'lola.ising', signedUp: '2026-11-04', when: 'Yesterday' },
  { username: 'pinegrove.cafe', signedUp: '2026-11-05', when: 'Today, 1:40 PM' },
]

/** Stable pale tints for people, so each one is recognisable across screens. */
export const tints = [
  { bg: 'var(--good-bg)', ink: 'var(--good-ink)' },
  { bg: 'var(--warn-bg)', ink: 'var(--warn-ink)' },
  { bg: 'var(--sale-bg)', ink: 'var(--sale-ink)' },
]
