import { apiRequest } from '@/lib/api-client'
import type { PageResponse } from '@/types/api'
import type {
  ChargingPlanPreviewResponse,
  ChargingSchedule,
  CreateChargingPlanPreviewRequest,
  CreateChargingScheduleRequest,
  ListChargingSchedulesParams,
} from './types'

/** POST /charging-plans/preview — ranked candidates, nothing persisted. */
export function previewChargingPlan(
  body: CreateChargingPlanPreviewRequest,
): Promise<ChargingPlanPreviewResponse> {
  return apiRequest<ChargingPlanPreviewResponse>('/charging-plans/preview', {
    method: 'POST',
    body,
  })
}

/** POST /charging-schedules — confirms one previewed candidate as a persisted schedule. */
export function createChargingSchedule(
  body: CreateChargingScheduleRequest,
): Promise<ChargingSchedule> {
  return apiRequest<ChargingSchedule>('/charging-schedules', { method: 'POST', body })
}

export function getChargingSchedule(scheduleId: number): Promise<ChargingSchedule> {
  return apiRequest<ChargingSchedule>(`/charging-schedules/${scheduleId}`)
}

export function listChargingSchedules(
  params: ListChargingSchedulesParams = {},
): Promise<PageResponse<ChargingSchedule>> {
  return apiRequest<PageResponse<ChargingSchedule>>('/charging-schedules', {
    query: { page: params.page, size: params.size },
  })
}
