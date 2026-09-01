/**
 * Shared API types. These mirror the schemas in `docs/openapi.yaml` and the DTOs actually
 * returned by the backend. Domain-specific request/response shapes live under
 * `src/features/<domain>/types.ts`.
 */

export type PriceArea = 'NO1' | 'NO2' | 'NO3' | 'NO4' | 'NO5'

export type AccountStatus = 'ACTIVE' | 'INACTIVE'

export interface User {
  id: number
  email: string
  name: string
  defaultPriceArea: PriceArea
  status: AccountStatus
  createdAt: string
  updatedAt: string
}

export interface PageMetadata {
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface PageResponse<T> {
  content: T[]
  page: PageMetadata
}

export interface FieldErrorDetail {
  field: string
  rejectedValue?: unknown
  reason: string
}

/** The `application/problem+json` body returned for every non-2xx response. */
export interface ApiErrorBody {
  timestamp: string
  status: number
  code: string
  message: string
  path: string
  traceId: string
  fieldErrors?: FieldErrorDetail[]
}
