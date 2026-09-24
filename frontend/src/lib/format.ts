/**
 * Display formatting. Timestamps from the API are UTC ISO strings; per
 * `docs/tech-stack-architecture.md` they are shown in the Europe/Oslo zone.
 */
const OSLO_TIME_ZONE = 'Europe/Oslo'

const dateTimeFormatter = new Intl.DateTimeFormat('en-GB', {
  timeZone: OSLO_TIME_ZONE,
  dateStyle: 'medium',
  timeStyle: 'short',
})

const dateFormatter = new Intl.DateTimeFormat('en-GB', {
  timeZone: OSLO_TIME_ZONE,
  dateStyle: 'medium',
})

export function formatDateTime(iso: string): string {
  return dateTimeFormatter.format(new Date(iso))
}

export function formatDate(iso: string): string {
  return dateFormatter.format(new Date(iso))
}

const shortDateFormatter = new Intl.DateTimeFormat('en-GB', {
  timeZone: OSLO_TIME_ZONE,
  day: 'numeric',
  month: 'short',
})

/**
 * Compact axis-label form, e.g. "1 Sep" — for a plain `YYYY-MM-DD` date (no time component).
 * Parsed as UTC midnight, not local midnight: the string is already an Oslo calendar date, and
 * reinterpreting it in the *viewer's* local timezone can roll it back a day once converted back to
 * Oslo (e.g. a Korean, UTC+9, browser: local midnight Sep 1 is Aug 31 17:00 Oslo time).
 */
export function formatShortDate(date: string): string {
  return shortDateFormatter.format(new Date(`${date}T00:00:00Z`))
}

const monthFormatter = new Intl.DateTimeFormat('en-GB', {
  timeZone: OSLO_TIME_ZONE,
  month: 'short',
  year: 'numeric',
})

/**
 * e.g. formatMonth("2026-09-01") -> "Sep 2026" — for a monthly-granularity `DailySavings.date`.
 * Parsed as UTC midnight for the same reason as {@link formatShortDate}.
 */
export function formatMonth(date: string): string {
  return monthFormatter.format(new Date(`${date}T00:00:00Z`))
}

const isoDateFormatter = new Intl.DateTimeFormat('en-CA', { timeZone: OSLO_TIME_ZONE })

/** Today's calendar date in Europe/Oslo, as `YYYY-MM-DD` — matches the backend's day boundary. */
export function todayIso(): string {
  return isoDateFormatter.format(new Date())
}

/**
 * Calendar-date arithmetic on a `YYYY-MM-DD` string, done in UTC so it is independent of the
 * browser's local timezone. Used for the Savings page's range presets.
 */
export function isoDateMinus(date: string, unit: 'days' | 'months', amount: number): string {
  const [year, month, day] = date.split('-').map(Number)
  const utc = new Date(Date.UTC(year, month - 1, day))
  if (unit === 'days') {
    utc.setUTCDate(utc.getUTCDate() - amount)
  } else {
    utc.setUTCMonth(utc.getUTCMonth() - amount)
  }
  return utc.toISOString().slice(0, 10)
}

const numberFormatter = new Intl.NumberFormat('en-GB', { maximumFractionDigits: 2 })

/** e.g. formatKw(7.4) -> "7.4 kW" */
export function formatKw(value: number): string {
  return `${numberFormatter.format(value)} kW`
}

export function formatKwh(value: number): string {
  return `${numberFormatter.format(value)} kWh`
}

const nokFormatter = new Intl.NumberFormat('en-GB', {
  style: 'currency',
  currency: 'NOK',
  currencyDisplay: 'code',
})

/** e.g. formatNok(12.5) -> "NOK 12.50". All charging prices from the API are in NOK. */
export function formatNok(value: number): string {
  return nokFormatter.format(value)
}

/** e.g. formatOrePerKwh(0.71) -> "71.0 øre/kWh" — prices per kWh are small NOK fractions. */
export function formatOrePerKwh(nokPerKwh: number): string {
  return `${new Intl.NumberFormat('en-GB', { maximumFractionDigits: 1 }).format(nokPerKwh * 100)} øre/kWh`
}

/**
 * e.g. formatNokPerKwh(1.42342) -> "1.42 NOK/kWh" — a blended cost-per-kWh figure (what charging
 * actually cost), as distinct from a raw spot price, which is shown in øre via {@link formatOrePerKwh}.
 */
export function formatNokPerKwh(value: number): string {
  return `${new Intl.NumberFormat('en-GB', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value)} NOK/kWh`
}

const percentFormatter = new Intl.NumberFormat('en-GB', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

/** e.g. formatPercent(26.956) -> "26.96%", formatPercent(-33.69) -> "-33.69%" */
export function formatPercent(value: number): string {
  return `${percentFormatter.format(value)}%`
}

/** e.g. formatDurationMinutes(273) -> "4h 33m", formatDurationMinutes(45) -> "45m" */
export function formatDurationMinutes(minutes: number): string {
  const rounded = Math.max(0, Math.round(minutes))
  const hours = Math.floor(rounded / 60)
  const mins = rounded % 60
  if (hours === 0) {
    return `${mins}m`
  }
  if (mins === 0) {
    return `${hours}h`
  }
  return `${hours}h ${mins}m`
}

/** Time-only rendering in the Europe/Oslo zone, e.g. "23:00". */
const timeFormatter = new Intl.DateTimeFormat('en-GB', {
  timeZone: 'Europe/Oslo',
  hour: '2-digit',
  minute: '2-digit',
})

export function formatTime(iso: string): string {
  return timeFormatter.format(new Date(iso))
}

/**
 * Splits a charging window into a date label and a time range for display.
 * When the window stays within one Oslo day the date is shown once; otherwise
 * the date label spans both days.
 */
export function formatScheduleWindow(
  startIso: string,
  endIso: string,
): { date: string; timeRange: string } {
  const startDate = dateFormatter.format(new Date(startIso))
  const endDate = dateFormatter.format(new Date(endIso))
  if (startDate === endDate) {
    return { date: startDate, timeRange: `${formatTime(startIso)} – ${formatTime(endIso)}` }
  }
  return {
    date: `${startDate} – ${endDate}`,
    timeRange: `${formatTime(startIso)} – ${formatTime(endIso)}`,
  }
}
