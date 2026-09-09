import { keepPreviousData, useQuery } from '@tanstack/react-query'

import { getChargingHistoryEntry, listChargingHistory } from './api'
import type { ListChargingHistoryParams } from './types'

export const historyKeys = {
  all: ['history'] as const,
  list: (params: ListChargingHistoryParams) => ['history', 'list', params] as const,
  detail: (sessionId: number) => ['history', 'detail', sessionId] as const,
}

export function useChargingHistoryQuery(params: ListChargingHistoryParams) {
  return useQuery({
    queryKey: historyKeys.list(params),
    queryFn: () => listChargingHistory(params),
    // Keep the current page visible while the next one loads, so Previous/Next doesn't flash empty.
    placeholderData: keepPreviousData,
  })
}

export function useChargingHistoryEntryQuery(sessionId: number) {
  return useQuery({
    queryKey: historyKeys.detail(sessionId),
    queryFn: () => getChargingHistoryEntry(sessionId),
    enabled: Number.isFinite(sessionId) && sessionId > 0,
  })
}
