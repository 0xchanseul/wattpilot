import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import {
  createChargingSchedule,
  getChargingSchedule,
  getChargingSchedulesOverview,
  previewChargingPlan,
} from './api'
import type { CreateChargingPlanPreviewRequest, CreateChargingScheduleRequest } from './types'

export const chargingKeys = {
  all: ['charging'] as const,
  schedules: () => ['charging', 'schedules'] as const,
  scheduleOverview: () => ['charging', 'schedules', 'overview'] as const,
  scheduleDetail: (scheduleId: number) =>
    ['charging', 'schedules', 'detail', scheduleId] as const,
}

/** Preview is a POST with no side effects; a mutation keeps the "run on demand" semantics explicit. */
export function useChargingPlanPreviewMutation() {
  return useMutation({
    mutationFn: (body: CreateChargingPlanPreviewRequest) => previewChargingPlan(body),
  })
}

export function useCreateChargingScheduleMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateChargingScheduleRequest) => createChargingSchedule(body),
    onSuccess: (schedule) => {
      queryClient.setQueryData(chargingKeys.scheduleDetail(schedule.id), schedule)
      void queryClient.invalidateQueries({ queryKey: chargingKeys.schedules() })
    },
  })
}

export function useChargingSchedulesQuery() {
  return useQuery({
    queryKey: chargingKeys.scheduleOverview(),
    queryFn: getChargingSchedulesOverview,
  })
}

export function useChargingScheduleQuery(scheduleId: number) {
  return useQuery({
    queryKey: chargingKeys.scheduleDetail(scheduleId),
    queryFn: () => getChargingSchedule(scheduleId),
    enabled: Number.isFinite(scheduleId) && scheduleId > 0,
  })
}
