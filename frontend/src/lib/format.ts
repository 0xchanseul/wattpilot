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
