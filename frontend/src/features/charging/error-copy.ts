import { ApiError } from '@/lib/api-error'
import { errorMessage } from '@/lib/error-message'

export interface ChargingErrorCopy {
  title: string
  /** What the user can do about it. */
  description: string
  /**
   * When true the conditions almost certainly need to change, so the UI should keep the user on
   * the input step rather than offering a plain "try again".
   */
  adjustConditions: boolean
}

/**
 * Maps the backend's application-level `code` (see `com.wattpilot.common.exception.ErrorCode`) to
 * user-facing guidance. Anything unrecognised falls back to the server's own message.
 */
const COPY: Record<string, ChargingErrorCopy> = {
  CHARGING_DEADLINE_TOO_SOON: {
    title: 'Not enough time before the deadline',
    description:
      'There is not enough time to reach the target charge before your deadline. Move the deadline later or lower the target level.',
    adjustConditions: true,
  },
  CHARGING_PRICE_DATA_INSUFFICIENT: {
    title: 'Prices not available for that window',
    description:
      'Electricity prices for the requested charging window have not been published yet. Try a deadline within the next day or two.',
    adjustConditions: true,
  },
  CHARGING_NO_CONTINUOUS_WINDOW: {
    title: 'No continuous window fits',
    description:
      'No gap-free block of prices is long enough to finish charging before the deadline. Move the deadline later or lower the target level.',
    adjustConditions: true,
  },
  CHARGING_CANDIDATE_UNAVAILABLE: {
    title: 'This option is no longer available',
    description:
      'Prices moved or the start time has passed since you previewed. Recalculate to get fresh options.',
    adjustConditions: false,
  },
  CHARGING_SCHEDULE_CONFLICT: {
    title: 'Overlapping schedule',
    description:
      'This EV already has an active charging schedule that overlaps the selected window. Pick a different window or cancel the existing schedule.',
    adjustConditions: false,
  },
  EV_NOT_FOUND: {
    title: 'EV unavailable',
    description: 'This EV could not be found or is no longer active. Choose another EV.',
    adjustConditions: true,
  },
  ELECTRICITY_PRICE_NOT_FOUND: {
    title: 'Prices not available',
    description: 'No electricity prices are available for the selected area and time.',
    adjustConditions: true,
  },
  VALIDATION_ERROR: {
    title: 'Check the charging conditions',
    description: 'Some values are out of range. Review the form and try again.',
    adjustConditions: true,
  },
}

export function chargingErrorCopy(error: unknown): ChargingErrorCopy {
  if (error instanceof ApiError && COPY[error.code]) {
    return COPY[error.code]
  }
  if (error instanceof ApiError && error.status === 0) {
    return {
      title: 'Cannot reach the server',
      description: 'Check your connection and try again.',
      adjustConditions: false,
    }
  }
  return { title: 'Something went wrong', description: errorMessage(error), adjustConditions: false }
}
