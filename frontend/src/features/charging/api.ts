import { apiRequest } from '@/lib/api-client'
import type {
  ChargingPlanPreviewResponse,
  ChargingSchedule,
  ChargingSchedulesOverview,
  CreateChargingPlanPreviewRequest,
  CreateChargingScheduleRequest,
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

/** GET /charging-schedules — the overview (upcoming / inProgress / recentActivity), not paginated. */
export function getChargingSchedulesOverview(): Promise<ChargingSchedulesOverview> {
  return apiRequest<ChargingSchedulesOverview>('/charging-schedules')
}
