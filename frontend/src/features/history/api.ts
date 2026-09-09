import { apiRequest } from '@/lib/api-client'
import type {
  ChargingHistoryDetail,
  ChargingHistoryListResponse,
  ListChargingHistoryParams,
} from './types'

/** GET /charging-history — paginated COMPLETED/FAILED sessions, newest first, plus a savings summary. */
export function listChargingHistory(
  params: ListChargingHistoryParams = {},
): Promise<ChargingHistoryListResponse> {
  return apiRequest<ChargingHistoryListResponse>('/charging-history', {
    query: { page: params.page, size: params.size, status: params.status },
  })
}

/** GET /charging-history/{sessionId} — the charging receipt for one executed session. */
export function getChargingHistoryEntry(sessionId: number): Promise<ChargingHistoryDetail> {
  return apiRequest<ChargingHistoryDetail>(`/charging-history/${sessionId}`)
}
