import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, getToken, setToken, setUnauthorizedHandler } from '../api/client'
import type { DemoRole, LoginResponse, RegisterRequest, UserResponse } from '../api/types'
import { AuthContext, type Auth } from './auth-context'

const USER_KEY = 'thriftbite.user'
const EXPIRES_KEY = 'thriftbite.expiresAt'

function savedUser(): UserResponse | null {
  try {
    const expires = localStorage.getItem(EXPIRES_KEY)
    if (!getToken() || !expires || new Date(expires).getTime() <= Date.now()) return null
    const raw = localStorage.getItem(USER_KEY)
    return raw ? (JSON.parse(raw) as UserResponse) : null
  } catch {
    return null
  }
}

function save(response: LoginResponse | null): void {
  setToken(response?.token ?? null)
  try {
    if (response) {
      localStorage.setItem(USER_KEY, JSON.stringify(response.user))
      localStorage.setItem(EXPIRES_KEY, response.expiresAt)
    } else {
      localStorage.removeItem(USER_KEY)
      localStorage.removeItem(EXPIRES_KEY)
    }
  } catch { /* storage unavailable: the login lasts until the tab closes */ }
}

/** Keeps who's logged in. The token lives in localStorage and goes on every API call. */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(savedUser)
  const [ready, setReady] = useState(() => savedUser() === null)

  const logout = useCallback(() => {
    save(null)
    setUser(null)
  }, [])

  useEffect(() => setUnauthorizedHandler(logout), [logout])

  // A saved login might be stale (e.g. the demo reset): check it once.
  useEffect(() => {
    if (ready) return
    let live = true
    api.me().then(
      (me) => { if (live) { setUser(me); setReady(true) } },
      () => { if (live) { logout(); setReady(true) } },
    )
    return () => { live = false }
  }, [ready, logout])

  const value = useMemo<Auth>(() => {
    const start = (response: LoginResponse) => {
      save(response)
      setUser(response.user)
      return response.user
    }
    return {
      user,
      ready,
      login: async (username, password) => start(await api.login(username, password)),
      demoLogin: async (as: DemoRole) => start(await api.demoLogin(as)),
      register: async (request: RegisterRequest) => {
        const created = await api.register(request)
        // Buyers can go straight in; stores wait for an admin.
        if (created.approved) return start(await api.login(request.username, request.password))
        return created
      },
      logout,
    }
  }, [user, ready, logout])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
