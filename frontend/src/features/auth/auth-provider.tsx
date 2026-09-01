import { useCallback, useEffect, useMemo, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'

import { authStore } from '@/lib/auth-store'
import { refreshAccessToken } from '@/lib/api-client'
import type { User } from '@/types/api'
import { AuthContext, type AuthStatus, type AuthContextValue } from './auth-context'
import {
  fetchCurrentUser,
  login as loginRequest,
  logout as logoutRequest,
  signUp as signUpRequest,
} from './api'
import type { LoginInput, SignUpInput } from './types'

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<AuthStatus>('loading')
  const [user, setUser] = useState<User | null>(null)

  // Restore the session on load: the access token lives only in memory, so a reload always
  // starts by exchanging the HttpOnly refresh cookie for a fresh one.
  useEffect(() => {
    let cancelled = false

    authStore.setOnSessionExpired(() => {
      setUser(null)
      setStatus('unauthenticated')
      queryClient.clear()
    })

    void (async () => {
      const refreshed = await refreshAccessToken()
      if (cancelled) return
      if (!refreshed) {
        setStatus('unauthenticated')
        return
      }
      try {
        const currentUser = await fetchCurrentUser()
        if (cancelled) return
        setUser(currentUser)
        setStatus('authenticated')
      } catch {
        if (cancelled) return
        setStatus('unauthenticated')
      }
    })()

    return () => {
      cancelled = true
      authStore.setOnSessionExpired(null)
    }
  }, [queryClient])

  const login = useCallback(async (input: LoginInput) => {
    const response = await loginRequest(input)
    authStore.setAccessToken(response.accessToken)
    setUser(response.user)
    setStatus('authenticated')
  }, [])

  const signUp = useCallback(async (input: SignUpInput) => {
    const response = await signUpRequest(input)
    authStore.setAccessToken(response.accessToken)
    setUser(response.user)
    setStatus('authenticated')
  }, [])

  const logout = useCallback(async () => {
    try {
      await logoutRequest()
    } catch {
      // A failed logout call (e.g. the access token already expired) still ends the local session.
    } finally {
      authStore.setAccessToken(null)
      setUser(null)
      setStatus('unauthenticated')
      queryClient.clear()
    }
  }, [queryClient])

  const value = useMemo<AuthContextValue>(
    () => ({ status, user, login, signUp, logout }),
    [status, user, login, signUp, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
