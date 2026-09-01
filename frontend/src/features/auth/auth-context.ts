import { createContext } from 'react'
import type { User } from '@/types/api'
import type { LoginInput, SignUpInput } from './types'

export type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated'

export interface AuthContextValue {
  status: AuthStatus
  user: User | null
  login: (input: LoginInput) => Promise<void>
  signUp: (input: SignUpInput) => Promise<void>
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)
