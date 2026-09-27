/**
 * Savings types. These mirror `com.wattpilot.savings.dto` and the `SavingsSummary` / `DailySavings`
 * schemas in `docs/openapi.yaml`.
 */

export type Granularity = 'DAILY' | 'MONTHLY'

/**
 * Realized totals over `[from, to]` (both inclusive, Europe/Oslo calendar days), optionally scoped
 * to one EV. `optimizedCostNok` is named after the plan's field but is backed by the realized
 * `actualCostNok` sum — same convention as the Dashboard/History summaries.
 */
export interface SavingsSummary {
  from: string
  to: string
  evId: number | null
  currency: 'NOK'
  completedSessionCount: number
  totalEnergyKwh: number
  optimizedCostNok: number
  baselineCostNok: number
  /** baselineCostNok - optimizedCostNok. */
  totalSavingsNok: number
  /** totalSavingsNok / baselineCostNok * 100; 0 when baselineCostNok is 0. */
  savingsRatePercent: number
}

/**
 * One point of `GET /savings/daily` — a calendar day, or the first day of a calendar month when
 * `granularity=MONTHLY`. Zero-filled for a bucket with no completed session.
 */
export interface DailySavings {
  date: string
  currency: 'NOK'
  sessionCount: number
  energyKwh: number
  optimizedCostNok: number
  baselineCostNok: number
  /** baselineCostNok - optimizedCostNok. */
  savingsNok: number
  /** savingsNok / baselineCostNok * 100; 0 when baselineCostNok is 0. */
  savingsRatePercent: number
}

export type PatternGroupBy = 'WEEKDAY' | 'HOUR_OF_DAY'

/**
 * One point of `GET /savings/patterns` — a recurring time bucket, not a calendar date. `bucket`
 * means ISO-8601 day of week (1=Monday..7=Sunday) for `groupBy=WEEKDAY`, or the starting hour of day
 * (0-23) for `groupBy=HOUR_OF_DAY`. Figures are summed across individual charging plan slots, so a
 * session spanning several hours (or crossing midnight) contributes to every bucket it actually ran
 * in, not only the one it started in. Zero-filled for a bucket with no contributing slot.
 */
export interface SavingsPatternPoint {
  bucket: number
  currency: 'NOK'
  sessionCount: number
  energyKwh: number
  optimizedCostNok: number
  baselineCostNok: number
  savingsNok: number
  savingsRatePercent: number
}

export interface SavingsQueryParams {
  from: string
  to: string
  evId?: number
}

export interface DailySavingsQueryParams extends SavingsQueryParams {
  granularity?: Granularity
}

export interface SavingsPatternQueryParams extends SavingsQueryParams {
  groupBy: PatternGroupBy
}
