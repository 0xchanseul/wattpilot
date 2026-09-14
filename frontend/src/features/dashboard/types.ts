import type { ScheduleStatus } from '@/features/charging/types'

/**
 * Dashboard types. These mirror `com.wattpilot.dashboard.dto` and the `Dashboard` schema in
 * `docs/openapi.yaml`. `nextCharging` and `currentPrice` are independently nullable; every other
 * block is always present, empty/zeroed when there is no data.
 */

/** All-time realized totals over every COMPLETED session. */
export interface DashboardSummary {
  /** baselineCostNok - actualCostNok, summed (realized, never the plan estimate). */
  totalSavingsNok: number
  totalEnergyKwh: number
  /** COMPLETED count. */
  totalSessions: number
  /** totalActualCostNok / totalEnergyKwh — energy-weighted, 0 when nothing has been charged yet. */
  averageCostPerKwh: number
}

/**
 * The caller's nearest active reservation: the IN_PROGRESS schedule if one is currently charging,
 * otherwise the earliest-starting WAITING one. `estimatedSavingsNok` is the plan's own prediction
 * (baseline - optimized) since the charge has not happened yet.
 */
export interface DashboardNextCharging {
  scheduleId: number
  evId: number
  evName: string
  startAt: string
  endAt: string
  estimatedEnergyKwh: number
  estimatedCostNok: number
  estimatedSavingsNok: number
  /** Only WAITING or IN_PROGRESS ever appear here. */
  status: Extract<ScheduleStatus, 'WAITING' | 'IN_PROGRESS'>
}

/** The current hour's price against today's Norwegian-calendar-day average. */
export interface DashboardCurrentPrice {
  priceNokPerKwh: number
  todayAveragePriceNokPerKwh: number | null
  /** (priceNokPerKwh - todayAveragePriceNokPerKwh) / todayAveragePriceNokPerKwh * 100. */
  differencePercent: number | null
}

/** One day's realized savings for the trend chart. */
export interface DashboardSavingsTrendPoint {
  date: string
  savingsNok: number
}

/**
 * Baseline vs. realized cost over the last 30 days of COMPLETED sessions. Despite the name,
 * `optimizedCostNok` is the realized `actualCostNok` sum (identical to the plan estimate in V1).
 */
export interface DashboardCostComparison {
  baselineCostNok: number
  optimizedCostNok: number
  savingsNok: number
  /** savingsNok / baselineCostNok * 100; 0 when baselineCostNok is 0. */
  savingsPercent: number
}

/** A completed charging session, slimmed down for the Dashboard's home-screen list. */
export interface DashboardRecentSession {
  sessionId: number
  evId: number
  evName: string
  startedAt: string | null
  endedAt: string | null
  actualEnergyKwh: number
  actualCostNok: number
  /** baselineCostNok - actualCostNok. */
  realizedSavingsNok: number
}

/** Response of `GET /dashboard`. */
export interface DashboardResponse {
  summary: DashboardSummary
  nextCharging: DashboardNextCharging | null
  currentPrice: DashboardCurrentPrice | null
  /** Last 30 Europe/Oslo calendar days, ascending, zero-filled. */
  savingsTrend: DashboardSavingsTrendPoint[]
  costComparison: DashboardCostComparison
  /** Last 3 COMPLETED sessions, newest first. */
  recentSessions: DashboardRecentSession[]
}
