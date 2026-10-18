import { API_BASE_URL } from '@/lib/env'

/**
 * Tells the backend a page was opened so it can count the visitor. Fire-and-forget: it carries no
 * token (anonymous visitors are counted too) and a failure must never affect the page.
 */
export function recordVisit(): void {
  fetch(`${API_BASE_URL}/visits`, { method: 'POST', keepalive: true }).catch(() => {})
}
