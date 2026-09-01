import { ApiError } from '@/lib/api-error'

/** A user-facing message for any thrown value, preferring the backend's own message. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError || error instanceof Error) {
    return error.message
  }
  return 'Something went wrong. Please try again.'
}

/**
 * Applies a validation ApiError's per-field messages to a react-hook-form `setError`. Returns true
 * when it handled the error, so the caller can fall back to a general message otherwise.
 */
export function applyFieldErrors(
  error: unknown,
  setError: (field: string, error: { type: string; message: string }) => void,
  knownFields: readonly string[],
): boolean {
  if (!(error instanceof ApiError) || error.fieldErrors.length === 0) {
    return false
  }
  let applied = false
  for (const detail of error.fieldErrors) {
    if (knownFields.includes(detail.field)) {
      setError(detail.field, { type: 'server', message: detail.reason })
      applied = true
    }
  }
  return applied
}
