import { apiRequest } from '@/lib/api-client'
import type { Analytics, AnalyticsQueryParams } from './types'

// The admin area has its own cookie session, so none of these calls carry the user access token or
// trigger the user token refresh.

/** POST /admin/login — sets the HttpOnly admin cookie on success. */
export function adminLogin(password: string): Promise<void> {
  return apiRequest<void>('/admin/login', { method: 'POST', body: { password }, withAuth: false })
}

/** POST /admin/logout — clears the admin cookie. */
export function adminLogout(): Promise<void> {
  return apiRequest<void>('/admin/logout', { method: 'POST', withAuth: false })
}

/**
 * GET /admin/session — resolves while the admin cookie is valid, otherwise rejects with 401. The
 * answer is 204 with no body, but a query function may not resolve to undefined, hence the `true`.
 */
export async function getAdminSession(): Promise<true> {
  await apiRequest<void>('/admin/session', { withAuth: false })
  return true
}

/** GET /admin/analytics */
export function getAnalytics(params: AnalyticsQueryParams): Promise<Analytics> {
  return apiRequest<Analytics>('/admin/analytics', {
    query: { from: params.from, to: params.to },
    withAuth: false,
  })
}
