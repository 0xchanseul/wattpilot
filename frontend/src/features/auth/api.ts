import { apiRequest } from '@/lib/api-client'
import type { User } from '@/types/api'
import type { AuthResponse, LoginInput, SignUpInput } from './types'

export function signUp(input: SignUpInput): Promise<AuthResponse> {
  return apiRequest<AuthResponse>('/auth/signup', { method: 'POST', body: input, withAuth: false })
}

export function login(input: LoginInput): Promise<AuthResponse> {
  return apiRequest<AuthResponse>('/auth/login', { method: 'POST', body: input, withAuth: false })
}

export function logout(): Promise<void> {
  return apiRequest<void>('/auth/logout', { method: 'POST' })
}

export function fetchCurrentUser(): Promise<User> {
  return apiRequest<User>('/users/me')
}
