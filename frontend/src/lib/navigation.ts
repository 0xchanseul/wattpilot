import { useLocation } from 'react-router'

/**
 * "Back to list" support for detail pages. A list page stamps its own location onto the router
 * state of each row link (via `listOrigin`); the matching detail page reads it back (via
 * `useBackTarget`) so "back" returns to the exact list the user came from — filters and page
 * included — and falls back to the section's canonical list when the page is opened from a
 * bookmark or after a refresh, where there is no origin state.
 */
export interface BackTarget {
  to: string
  label: string
}

export interface ListOriginState {
  from: string
  fromLabel: string
}

export function listOrigin(to: string, label: string): ListOriginState {
  return { from: to, fromLabel: label }
}

export function useBackTarget(fallback: BackTarget): BackTarget {
  const { state } = useLocation()
  const origin = (state ?? null) as Partial<ListOriginState> | null
  if (origin?.from && origin.fromLabel) {
    return { to: origin.from, label: origin.fromLabel }
  }
  return fallback
}
