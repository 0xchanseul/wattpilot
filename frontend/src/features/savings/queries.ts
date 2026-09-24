import { useQuery } from '@tanstack/react-query'

import { getDailySavings, getSavingsSummary } from './api'
import type { DailySavingsQueryParams, SavingsQueryParams } from './types'

export const savingsKeys = {
  all: ['savings'] as const,
  summary: (params: SavingsQueryParams) => ['savings', 'summary', params] as const,
  daily: (params: DailySavingsQueryParams) => ['savings', 'daily', params] as const,
}

export function useSavingsSummaryQuery(params: SavingsQueryParams) {
  return useQuery({
    queryKey: savingsKeys.summary(params),
    queryFn: () => getSavingsSummary(params),
  })
}

export function useDailySavingsQuery(params: DailySavingsQueryParams) {
  return useQuery({
    queryKey: savingsKeys.daily(params),
    queryFn: () => getDailySavings(params),
  })
}
