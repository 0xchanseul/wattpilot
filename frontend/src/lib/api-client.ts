import { API_BASE_URL } from '@/lib/env'
import { ApiError } from '@/lib/api-error'
import { authStore } from '@/lib/auth-store'
import type { ApiErrorBody } from '@/types/api'

type QueryValue = string | number | boolean | undefined | null

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE'
  body?: unknown
  query?: Record<string, QueryValue>
  signal?: AbortSignal
  /**
   * When false, no access token is attached and a 401 is surfaced as-is instead of triggering a
   * refresh-and-retry. Used by the auth endpoints themselves.
   */
  withAuth?: boolean
}

function buildUrl(path: string, query?: Record<string, QueryValue>): string {
  const url = new URL(`${API_BASE_URL}${path}`)
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== null && value !== '') {
        url.searchParams.set(key, String(value))
      }
    }
  }
  return url.toString()
}

async function parseError(response: Response): Promise<ApiError> {
  let body: Partial<ApiErrorBody> | null = null
  try {
    const contentType = response.headers.get('content-type') ?? ''
    if (contentType.includes('json')) {
      body = (await response.json()) as ApiErrorBody
    }
  } catch {
    body = null
  }
  return new ApiError(response.status, body, `Request failed with status ${response.status}`)
}

// --- Single-flight refresh -------------------------------------------------

let refreshInFlight: Promise<boolean> | null = null

async function runRefresh(): Promise<boolean> {
  try {
    const response = await fetch(buildUrl('/auth/refresh'), {
      method: 'POST',
      credentials: 'include',
    })
    if (!response.ok) {
      return false
    }
    const data = (await response.json()) as { accessToken: string }
    authStore.setAccessToken(data.accessToken)
    return true
  } catch {
    return false
  }
}

/**
 * Refreshes the access token using the HttpOnly refresh cookie. Concurrent callers share one
 * in-flight request so a burst of 401s does not rotate the refresh token several times over.
 */
export function refreshAccessToken(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = runRefresh().finally(() => {
      refreshInFlight = null
    })
  }
  return refreshInFlight
}

// --- Core request --------------------------------------------------------

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, query, signal, withAuth = true } = options
  const url = buildUrl(path, query)

  const send = async (): Promise<Response> => {
    const headers: Record<string, string> = {}
    if (withAuth) {
      const token = authStore.getAccessToken()
      if (token) {
        headers.Authorization = `Bearer ${token}`
      }
    }
    let payload: string | undefined
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json'
      payload = JSON.stringify(body)
    }
    try {
      return await fetch(url, { method, headers, body: payload, credentials: 'include', signal })
    } catch (cause) {
      throw ApiError.network(cause)
    }
  }

  let response = await send()

  if (response.status === 401 && withAuth) {
    const refreshed = await refreshAccessToken()
    if (refreshed) {
      response = await send()
    } else {
      authStore.notifySessionExpired()
      throw await parseError(response)
    }
  }

  if (!response.ok) {
    throw await parseError(response)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}
