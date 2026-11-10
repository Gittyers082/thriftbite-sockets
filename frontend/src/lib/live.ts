import { API_BASE, getToken } from '../api/client'
import type { ItemStatus } from '../api/types'

/**
 * The live connection: one WebSocket to the API, shared by every screen.
 *
 * Messages are hints, not data. Apart from a listing's public stock count, a
 * message only says "this part of your screen is out of date", and the screen
 * reloads it through the normal API. Nothing private travels over the socket,
 * and every screen still works (after a reload) when the socket is down.
 */
export type LiveMessage =
  /** A listing's stock or status changed. The same numbers anyone sees on the browse page. */
  | { type: 'item'; id: string; quantity: number; status: ItemStatus }
  /** Many listings changed at once (food expired, the demo reset). */
  | { type: 'deals' }
  /** This store's pickups or listings changed. */
  | { type: 'store' }
  /** One of this buyer's reservations changed. */
  | { type: 'reservations' }
  /** The stores waiting for approval changed. */
  | { type: 'approvals' }
  /** Made here, not sent by the server: the connection was down and is back, so anything may have been missed. */
  | { type: 'reconnected' }

/** 'connecting' is the first try; 'reconnecting' means it dropped or failed and we're trying again. */
export type LiveStatus = 'off' | 'connecting' | 'live' | 'reconnecting'

type Listener = (message: LiveMessage) => void

const URL = API_BASE.replace(/^http/, 'ws') + '/ws'
/** A hidden tab lets go of its connection after this long, so a forgotten tab doesn't keep the server busy. */
const HIDDEN_GRACE_MS = 60_000
/** Moving between screens drops to zero listeners for a moment; don't reconnect for that. */
const IDLE_GRACE_MS = 5_000
const MAX_RETRY_MS = 30_000

const listeners = new Set<Listener>()
const statusListeners = new Set<() => void>()
let socket: WebSocket | null = null
let status: LiveStatus = 'off'
let failures = 0
let missed = false
let retryTimer: number | undefined
let hiddenTimer: number | undefined
let idleTimer: number | undefined

function setStatus(next: LiveStatus): void {
  if (status === next) return
  status = next
  statusListeners.forEach((listener) => listener())
}

function emit(message: LiveMessage): void {
  listeners.forEach((listener) => listener(message))
}

function parse(data: unknown): LiveMessage | null {
  if (typeof data !== 'string') return null
  let value: unknown
  try { value = JSON.parse(data) } catch { return null }
  if (typeof value !== 'object' || value === null) return null
  const message = value as Record<string, unknown>
  switch (message.type) {
    case 'item':
      return typeof message.id === 'string' && typeof message.quantity === 'number' && typeof message.status === 'string'
        ? { type: 'item', id: message.id, quantity: message.quantity, status: message.status as ItemStatus }
        : null
    case 'deals':
    case 'store':
    case 'reservations':
    case 'approvals':
      return { type: message.type }
    default:
      return null   // "ready", "auth_failed", or something newer than this page
  }
}

function connect(): void {
  window.clearTimeout(retryTimer)
  if (socket || listeners.size === 0 || document.visibilityState !== 'visible') return
  setStatus(missed ? 'reconnecting' : 'connecting')

  let ws: WebSocket
  try {
    ws = new WebSocket(URL)
  } catch {
    setStatus('off')   // e.g. blocked by the browser; the app works without it
    return
  }
  socket = ws

  ws.onopen = () => {
    if (socket !== ws) return
    // The login goes in a message, not the URL, because URLs end up in server logs.
    const token = getToken()
    if (token) ws.send(JSON.stringify({ type: 'auth', token }))
    failures = 0
    setStatus('live')
    if (missed) {
      missed = false
      emit({ type: 'reconnected' })
    }
  }
  ws.onmessage = (event) => {
    if (socket !== ws) return
    const message = parse(event.data)
    if (message) emit(message)
  }
  ws.onclose = () => {
    if (socket !== ws) return
    socket = null
    missed = true
    if (listeners.size === 0 || document.visibilityState !== 'visible') {
      setStatus('off')
      return
    }
    // 1 s, 2 s, 4 s ... up to 30 s, plus a little randomness so every tab doesn't retry at once.
    const wait = Math.min(MAX_RETRY_MS, 1000 * 2 ** failures) + Math.random() * 500
    failures += 1
    setStatus('reconnecting')
    retryTimer = window.setTimeout(connect, wait)
  }
}

function disconnect(): void {
  window.clearTimeout(retryTimer)
  const ws = socket
  socket = null   // its handlers now ignore whatever happens next
  if (ws) ws.close()
  setStatus('off')
}

/** Starts listening. Returns the function that stops. The connection opens with the first listener. */
export function subscribeLive(listener: Listener): () => void {
  window.clearTimeout(idleTimer)
  listeners.add(listener)
  connect()
  return () => {
    listeners.delete(listener)
    if (listeners.size === 0) {
      idleTimer = window.setTimeout(() => { if (listeners.size === 0) disconnect() }, IDLE_GRACE_MS)
    }
  }
}

/** Call after logging in or out: the connection restarts so the server knows who is listening now. */
export function liveLoginChanged(): void {
  if (!socket) return
  disconnect()
  connect()
}

export function liveStatus(): LiveStatus {
  return status
}

export function subscribeLiveStatus(listener: () => void): () => void {
  statusListeners.add(listener)
  return () => { statusListeners.delete(listener) }
}

document.addEventListener('visibilitychange', () => {
  window.clearTimeout(hiddenTimer)
  if (document.visibilityState === 'hidden') {
    hiddenTimer = window.setTimeout(() => {
      if (!socket) return
      disconnect()
      missed = true
    }, HIDDEN_GRACE_MS)
  } else if (!socket) {
    failures = 0
    connect()
  }
})

// Back online: try now instead of waiting out the retry timer.
window.addEventListener('online', () => {
  if (socket) return
  failures = 0
  connect()
})
