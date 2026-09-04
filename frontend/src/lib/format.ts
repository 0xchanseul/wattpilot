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
