import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { markOperatorBrowser } from '@/lib/record-visit'
import { adminLogin, adminLogout, getAdminSession, getAnalytics } from './api'
import type { AnalyticsQueryParams } from './types'

export const adminKeys = {
  all: ['admin'] as const,
  session: ['admin', 'session'] as const,
  analytics: (params: AnalyticsQueryParams) => ['admin', 'analytics', params] as const,
}

export function useAdminSessionQuery() {
  return useQuery({
    queryKey: adminKeys.session,
    queryFn: getAdminSession,
    // A 401 here is the normal "not logged in" answer, not something to retry.
    retry: false,
    staleTime: Infinity,
  })
}

export function useAnalyticsQuery(params: AnalyticsQueryParams, enabled: boolean) {
  return useQuery({
    queryKey: adminKeys.analytics(params),
    queryFn: () => getAnalytics(params),
    enabled,
    retry: false,
  })
}

export function useAdminLoginMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: adminLogin,
    // Here rather than in the form: the login form unmounts as soon as the session query resets, and
    // a callback passed to mutate() is dropped once its component is gone.
    onSuccess: () => {
      markOperatorBrowser()
      return queryClient.resetQueries({ queryKey: adminKeys.all })
    },
  })
}

export function useAdminLogoutMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: adminLogout,
    onSettled: () => queryClient.resetQueries({ queryKey: adminKeys.all }),
  })
}
