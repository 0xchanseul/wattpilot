import { apiRequest } from '@/lib/api-client'
import type { DailySavings, DailySavingsQueryParams, SavingsQueryParams, SavingsSummary } from './types'

/** GET /savings/summary — realized totals over [from, to] (both inclusive), optionally by EV. */
export function getSavingsSummary(params: SavingsQueryParams): Promise<SavingsSummary> {
  return apiRequest<SavingsSummary>('/savings/summary', {
    query: { from: params.from, to: params.to, evId: params.evId },
  })
}

/** GET /savings/daily — zero-filled savings points, daily or (granularity=MONTHLY) monthly. */
export function getDailySavings(params: DailySavingsQueryParams): Promise<DailySavings[]> {
  return apiRequest<DailySavings[]>('/savings/daily', {
    query: { from: params.from, to: params.to, evId: params.evId, granularity: params.granularity },
  })
}
