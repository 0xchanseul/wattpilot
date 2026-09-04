import type { PriceArea } from '@/types/api'

/**
 * Charging types. These mirror the DTOs in `com.wattpilot.charging.dto` and the schemas in
 * `docs/openapi.yaml`. Money is NOK, energy is kWh, timestamps are ISO strings in Europe/Oslo.
 */

export type ScheduleStatus =
  | 'CREATED'
  | 'WAITING'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'FAILED'

/** One consecutive price slot inside a continuous charging window. */
export interface ChargingPlanSlot {
  startsAt: string
  endsAt: string
  pricePerKwh: number
  /** Grid-side energy drawn during this slot; slot energies sum to expectedEnergyKwh. */
  plannedEnergyKwh: number
  expectedCostNok: number
}

/** One ranked continuous charging window from a preview. Nothing here is persisted. */
export interface ChargingCandidate {
  /** 1 is the cheapest. */
  rank: number
  recommendedStartAt: string
  recommendedEndAt: string
  /** Grid-side energy over the window (battery target / efficiency). */
  expectedEnergyKwh: number
  estimatedCostNok: number
  /** Cost of charging immediately from "now"; identical for every candidate. */
  baselineCostNok: number
  /** baselineCostNok - estimatedCostNok. */
  expectedSavingsNok: number
  slots: ChargingPlanSlot[]
}

/** Request body for `POST /charging-plans/preview`. The window always starts at "now". */
export interface CreateChargingPlanPreviewRequest {
  evId: number
  currentBatteryPercent: number
  targetBatteryPercent: number
  /** ISO date-time with offset. */
  requiredCompletionAt: string
  priceArea: PriceArea
}

/** Response of `POST /charging-plans/preview`. */
export interface ChargingPlanPreviewResponse {
  evId: number
  currentBatteryPercent: number
  targetBatteryPercent: number
  requiredCompletionAt: string
  priceArea: PriceArea
  /** Battery-side energy to add (efficiency not applied). */
  calculatedEnergyKwh: number
  /** min(EV max AC power, charger power), before efficiency. */
  effectiveChargingPowerKw: number
  estimatedDurationMinutes: number
  /** Cheapest first, at most 3 entries. */
  candidates: ChargingCandidate[]
}

/**
 * Request body for `POST /charging-schedules`. The original preview conditions plus the picked
 * candidate's window. The server never trusts a client-sent cost, energy or slot list.
 */
export interface CreateChargingScheduleRequest {
  evId: number
  currentBatteryPercent: number
  targetBatteryPercent: number
  requiredCompletionAt: string
  priceArea: PriceArea
  selectedStartAt: string
  selectedEndAt: string
}

/** Response of the `/charging-schedules` endpoints. */
export interface ChargingSchedule {
  id: number
  planId: number
  evId: number
  status: ScheduleStatus
  scheduledStartAt: string
  scheduledEndAt: string
  /** Battery-side energy to add. */
  calculatedEnergyKwh: number
  /** Grid-side energy drawn (includes efficiency loss). */
  expectedEnergyKwh: number
  estimatedCostNok: number
  baselineCostNok: number
  expectedSavingsNok: number
  slots: ChargingPlanSlot[]
  createdAt: string
  updatedAt: string
}

export interface ListChargingSchedulesParams {
  page?: number
  size?: number
}
