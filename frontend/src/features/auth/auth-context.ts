import { createContext } from 'react'
import type { User } from '@/types/api'
import type { LoginInput, SignUpInput, UpdateProfileInput } from './types'

export type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated'

export interface AuthContextValue {
  status: AuthStatus
  user: User | null
  login: (input: LoginInput) => Promise<void>
  signUp: (input: SignUpInput) => Promise<void>
  startDemo: () => Promise<void>
  logout: () => Promise<void>
  /** Permanently deletes the account; on success the local session ends as after a logout. */
  deleteAccount: (password: string) => Promise<void>
  updateProfile: (input: UpdateProfileInput) => Promise<User>
}

export const AuthContext = createContext<AuthContextValue | null>(null)
