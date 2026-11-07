import { createContext, useContext } from 'react'
import type { DemoRole, RegisterRequest, UserResponse } from '../api/types'

export interface Auth {
  user: UserResponse | null
  /** False until a saved login has been checked with the API. */
  ready: boolean
  login: (username: string, password: string) => Promise<UserResponse>
  demoLogin: (as: DemoRole) => Promise<UserResponse>
  register: (request: RegisterRequest) => Promise<UserResponse>
  logout: () => void
}

export const AuthContext = createContext<Auth | null>(null)

export function useAuth(): Auth {
  const auth = useContext(AuthContext)
  if (!auth) throw new Error('useAuth must be used inside <AuthProvider>')
  return auth
}

/** Where each kind of account starts. */
export function homeFor(user: UserResponse | null): string {
  if (user?.role === 'OWNER') return '/owner'
  if (user?.role === 'ADMIN') return '/admin'
  return '/'
}
