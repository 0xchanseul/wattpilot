export interface AnalyticsSummary {
  visitorDays: number
  newSignups: number
  totalSignups: number
  signupsPer100VisitorDays: number | null
  schedulesCreated: number
  usersWithSchedule: number
  totalUsers: number
  activationRate: number | null
}

export interface DailyPoint {
  date: string
  visitors: number
  signups: number
  schedules: number
}

export interface SourceShare {
  /** Null for visits recorded before sources were tracked. */
  source: string | null
  visitors: number
  share: number
}

export interface CountryShare {
  /** Null when the country is unknown. */
  countryCode: string | null
  name: string
  visitors: number
  share: number
}

export interface Analytics {
  from: string | null
  to: string
  summary: AnalyticsSummary
  daily: DailyPoint[]
  sources: SourceShare[]
  countries: CountryShare[]
}

export interface AnalyticsQueryParams {
  /** Omitted for all time. */
  from?: string
  to: string
}
