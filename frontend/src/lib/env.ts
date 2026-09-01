/**
 * Reads the build-time environment. `VITE_API_BASE_URL` is required; failing fast here beats a
 * confusing "fetch failed" later.
 */
function requireEnv(name: keyof ImportMetaEnv): string {
  const value = import.meta.env[name]
  if (!value) {
    throw new Error(`Missing required environment variable: ${name}. Copy .env.example to .env.`)
  }
  return value
}

/** Base URL of the WattPilot backend REST API, e.g. http://localhost:8081/api/v1 (no trailing slash). */
export const API_BASE_URL = requireEnv('VITE_API_BASE_URL').replace(/\/$/, '')
