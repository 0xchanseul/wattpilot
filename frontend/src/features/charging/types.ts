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

export type SessionStatus = 'STARTED' | 'COMPLETED' | 'FAILED' | 'CANCELLED'

export type ChargingFailureCode =
  | 'CHARGER_UNAVAILABLE'
  | 'VEHICLE_DISCONNECTED'
  | 'START_REJECTED'
  | 'CHARGING_INTERRUPTED'
  | 'MISSED_EXECUTION_WINDOW'
  | 'SYSTEM_ERROR'

/**
 * A schedule's Mock Charging execution outcome. Null until the 1-minute execution scheduler makes
 * its first attempt. `failureCode` / `failureReason` are set only when `status` is `FAILED`.
 */
export interface ChargingSessionSummary {
  status: SessionStatus
  startedAt: string | null
  completedAt: string | null
  actualEnergyKwh: number | null
  actualCostNok: number | null
  baselineCostNok: number | null
  optimizedCostNok: number | null
  estimatedSavingsNok: number | null
  failureCode: ChargingFailureCode | null
  failureReason: string | null
}

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
  /** Reference cost at the window-average electricity price; identical for every candidate. */
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

/** Response of `GET /charging-schedules/{scheduleId}` and the items in the overview's active blocks. */
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
  /** Execution outcome; null while the schedule is still WAITING. */
  session: ChargingSessionSummary | null
  createdAt: string
  updatedAt: string
}

/**
 * A slim record of a just-finished schedule in the Schedules overview. `status` is `COMPLETED` or
 * `FAILED`. The full result picture (costs, savings, per-hour breakdown) is in the charging-history
 * endpoints, not here.
 */
export interface ChargingScheduleRecentActivity {
  scheduleId: number
  sessionId: number | null
  evId: number
  evName: string
  status: ScheduleStatus
  scheduledStartAt: string
  scheduledEndAt: string
  startedAt: string | null
  completedAt: string | null
  actualEnergyKwh: number | null
}

/**
 * Response of `GET /charging-schedules`. Not paginated: it is the "current and near-future" view,
 * not a history. `upcoming` (WAITING, earliest start first) and `inProgress` (IN_PROGRESS) are full
 * schedule views; `recentActivity` is the last 5 finished charges as a slim view.
 */
export interface ChargingSchedulesOverview {
  upcoming: ChargingSchedule[]
  inProgress: ChargingSchedule[]
  recentActivity: ChargingScheduleRecentActivity[]
}
