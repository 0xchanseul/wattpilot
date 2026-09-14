import { apiRequest } from '@/lib/api-client'
import type { DashboardResponse } from './types'

/** GET /dashboard — the home-screen aggregate: summary, next charging, price, trend, and history. */
export function getDashboard(): Promise<DashboardResponse> {
  return apiRequest<DashboardResponse>('/dashboard')
}
