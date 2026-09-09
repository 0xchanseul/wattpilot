import type { PageMetadata } from '@/types/api'
import type { PriceArea } from '@/types/api'
import type {
  ChargingFailureCode,
  ChargingPlanSlot,
  SessionStatus,
} from '@/features/charging/types'

/**
 * Charging-history types. These mirror `com.wattpilot.history.dto` and the schemas in
 * `docs/openapi.yaml`. History only ever contains terminal executions, so `status` is always
 * `COMPLETED` or `FAILED`.
 */

/** The two statuses a charging-history entry can have. */
export type HistoryStatus = Extract<SessionStatus, 'COMPLETED' | 'FAILED'>

/** EV figures snapshotted onto the plan at confirmation time (`EvSnapshot` schema). */
export interface EvSnapshot {
  name: string
  manufacturer: string
  model: string
  batteryCapacityKwh: number
  maxAcChargingPowerKw: number
  defaultChargerPowerKw: number
}

/**
 * One executed charging session. Cost/energy fields are non-null only for a `COMPLETED` session;
 * a `FAILED` one carries `failureCode` / `failureReason` instead.
 *
 * - `optimizedCostNok` / `estimatedSavingsNok` (`baseline - optimized`): what the plan predicted.
 * - `actualCostNok` / `actualEnergyKwh` / `realizedSavingsNok` (`baseline - actual`): what was delivered.
 */
export interface ChargingHistoryItem {
  sessionId: number
  scheduleId: number
  evId: number
  evName: string
  status: HistoryStatus
  /** When the outcome was recorded (the list's sort key); present on every entry. */
  recordedAt: string
  startedAt: string | null
  completedAt: string | null
  baselineCostNok: number | null
  optimizedCostNok: number | null
  estimatedSavingsNok: number | null
  actualEnergyKwh: number | null
  actualCostNok: number | null
  realizedSavingsNok: number | null
  failureCode: ChargingFailureCode | null
  failureReason: string | null
}

/**
 * Realized totals over every COMPLETED + FAILED session in scope (respects `evId`, ignores the
 * list's `status` filter and pagination). `totalEnergyKwh` / `totalSavingsNok` sum the COMPLETED
 * sessions only; savings are realized (`baseline - actual`).
 */
export interface ChargingHistorySummary {
  totalSessions: number
  /** COMPLETED as a percentage of totalSessions, one decimal (e.g. 83.3). */
  successRate: number
  totalEnergyKwh: number
  totalSavingsNok: number
}

/** Response of `GET /charging-history`. Standard `content` + `page`, plus the `summary` header. */
export interface ChargingHistoryListResponse {
  summary: ChargingHistorySummary
  content: ChargingHistoryItem[]
  page: PageMetadata
}

/**
 * The "charging receipt" — `GET /charging-history/{sessionId}`. Three groups kept separate so a
 * plan estimate is never read as a realized result: conditions, plan, and actual outcome.
 *
 * The plan group (recommended window, planned energy/cost, `plannedSlots`) is present even for a
 * `FAILED` entry — the plan succeeded, only the execution failed. The actual group is non-null
 * only for `COMPLETED`.
 */
export interface ChargingHistoryDetail {
  sessionId: number
  scheduleId: number
  planId: number
  evId: number
  evSnapshot: EvSnapshot
  startBatteryPercent: number
  targetBatteryPercent: number
  priceArea: PriceArea
  earliestStartAt: string
  requiredCompletionAt: string
  recommendedStartAt: string
  recommendedEndAt: string
  /** Battery-side energy added. */
  calculatedEnergyKwh: number
  effectiveChargingPowerKw: number
  estimatedDurationMinutes: number
  /** Grid-side energy the plan drew (includes efficiency loss). */
  plannedEnergyKwh: number
  optimizedCostNok: number
  baselineCostNok: number
  /** baselineCostNok - optimizedCostNok. */
  estimatedSavingsNok: number
  status: HistoryStatus
  /** When the outcome was recorded; present even when the charge never started. */
  recordedAt: string
  startedAt: string | null
  completedAt: string | null
  actualEnergyKwh: number | null
  actualCostNok: number | null
  /** baselineCostNok - actualCostNok; COMPLETED only. */
  realizedSavingsNok: number | null
  failureCode: ChargingFailureCode | null
  failureReason: string | null
  /** The plan's per-hour figures, calculation order. Planned, not a per-hour actual. */
  plannedSlots: ChargingPlanSlot[]
}

export interface ListChargingHistoryParams {
  page?: number
  size?: number
  status?: HistoryStatus
}
