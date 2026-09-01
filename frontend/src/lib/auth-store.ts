/**
 * Holds the access token in memory only (never localStorage), so a page reload starts with no
 * token and the app silently re-authenticates from the HttpOnly refresh cookie.
 *
 * This module is the seam between the API client and the auth context: the client reads the token
 * and reports a dead session here, the context writes the token and registers the callback.
 */
let accessToken: string | null = null
let onSessionExpired: (() => void) | null = null

export const authStore = {
  getAccessToken: (): string | null => accessToken,

  setAccessToken: (token: string | null): void => {
    accessToken = token
  },

  /** Registered once by the auth context; invoked when a refresh attempt fails. */
  setOnSessionExpired: (callback: (() => void) | null): void => {
    onSessionExpired = callback
  },

  notifySessionExpired: (): void => {
    accessToken = null
    onSessionExpired?.()
  },
}
