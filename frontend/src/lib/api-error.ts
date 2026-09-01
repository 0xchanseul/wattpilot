import type { ApiErrorBody, FieldErrorDetail } from '@/types/api'

/**
 * Thrown for every non-2xx API response. Carries the backend's application-level `code` and, for
 * validation failures, the per-field details so a form can show them inline.
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly traceId?: string
  readonly fieldErrors: FieldErrorDetail[]

  constructor(status: number, body: Partial<ApiErrorBody> | null, fallbackMessage: string) {
    super(body?.message ?? fallbackMessage)
    this.name = 'ApiError'
    this.status = status
    this.code = body?.code ?? 'UNKNOWN'
    this.traceId = body?.traceId
    this.fieldErrors = body?.fieldErrors ?? []
  }

  /** True when the server could not be reached at all (network error, CORS, server down). */
  static network(cause: unknown): ApiError {
    const error = new ApiError(0, null, 'Could not reach the server. Check your connection and try again.')
    error.cause = cause
    return error
  }

  fieldError(field: string): string | undefined {
    return this.fieldErrors.find((detail) => detail.field === field)?.reason
  }
}
