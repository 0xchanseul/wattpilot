import { API_BASE_URL } from '@/lib/env'

// Set in the operator's own browser so their visits do not count as traffic. Never sent anywhere.
const OPERATOR_FLAG_KEY = 'wp_no_track'

const ADMIN_PATH = /^\/admin(\/|$)/

/** Marks this browser as the operator's, so later visits are not counted. */
export function markOperatorBrowser(): void {
  try {
    localStorage.setItem(OPERATOR_FLAG_KEY, '1')
  } catch {
    // Storage can be blocked; the admin path itself is still never counted.
  }
}

function isOperatorBrowser(): boolean {
  try {
    return localStorage.getItem(OPERATOR_FLAG_KEY) === '1'
  } catch {
    return false
  }
}

/** The page that linked here, or empty for a direct visit and for navigation inside this site. */
function externalReferrer(): string {
  const referrer = document.referrer
  if (!referrer) return ''
  try {
    return new URL(referrer).origin === window.location.origin ? '' : referrer
  } catch {
    return ''
  }
}

/**
 * Tells the backend a page was opened so it can count the visitor, along with where they came from
 * (the referrer and the `utm_source` of the landing URL). Fire-and-forget: it carries no token
 * (anonymous visitors are counted too) and a failure must never affect the page.
 */
export function recordVisit(): void {
  if (ADMIN_PATH.test(window.location.pathname) || isOperatorBrowser()) return

  const body = {
    referrer: externalReferrer(),
    utmSource: new URLSearchParams(window.location.search).get('utm_source') ?? '',
  }
  fetch(`${API_BASE_URL}/visits`, {
    method: 'POST',
    keepalive: true,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }).catch(() => {})
}
