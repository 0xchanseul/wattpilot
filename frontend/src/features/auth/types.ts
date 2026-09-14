import type { PriceArea, User } from '@/types/api'

export interface LoginInput {
  email: string
  password: string
  /** When true, ask the backend for the longer "keep me signed in" session lifetime. */
  rememberMe: boolean
}

export interface SignUpInput {
  email: string
  password: string
  name: string
  defaultPriceArea: PriceArea
}

/** Response of POST /auth/signup and POST /auth/login. The refresh token is not here — it is set
 *  as an HttpOnly cookie on the same response. */
export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: User
}
