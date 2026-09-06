import type { ChargingFailureCode } from './types'

/**
 * Maps the backend's `failureCode` (see `com.wattpilot.charging.entity.ChargingFailureCode`) to a
 * short user-facing explanation. The server also sends a free-text `failureReason`, but it is not
 * always suitable for display, so the code-based copy is preferred when the code is recognised.
 */
const COPY: Record<ChargingFailureCode, string> = {
  CHARGER_UNAVAILABLE: 'The charger was unavailable when charging was due to start.',
  VEHICLE_DISCONNECTED: 'The vehicle was not connected to the charger.',
  START_REJECTED: 'The charger rejected the request to start charging.',
  CHARGING_INTERRUPTED: 'Charging started but was interrupted before it finished.',
  MISSED_EXECUTION_WINDOW: 'The scheduled window passed before charging could start.',
  SYSTEM_ERROR: 'A technical problem stopped the charging session.',
}

export function chargingFailureText(
  code: ChargingFailureCode | null,
  reason: string | null,
): string | null {
  if (code && COPY[code]) {
    return COPY[code]
  }
  return reason ?? null
}
