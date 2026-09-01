import { Navigate, Outlet, useLocation } from 'react-router'

import { FullPageSpinner } from '@/components/full-page-spinner'
import { useAuth } from '@/features/auth/use-auth'

/** Gates the authenticated area; sends signed-out users to /login and remembers where they were. */
export function ProtectedRoute() {
  const { status } = useAuth()
  const location = useLocation()

  if (status === 'loading') {
    return <FullPageSpinner />
  }
  if (status === 'unauthenticated') {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  return <Outlet />
}

/** Login and sign-up: bounce already-authenticated users into the app. */
export function PublicOnlyRoute() {
  const { status } = useAuth()

  if (status === 'loading') {
    return <FullPageSpinner />
  }
  if (status === 'authenticated') {
    return <Navigate to="/evs" replace />
  }
  return <Outlet />
}

export function RootRedirect() {
  const { status } = useAuth()

  if (status === 'loading') {
    return <FullPageSpinner />
  }
  return <Navigate to={status === 'authenticated' ? '/evs' : '/login'} replace />
}
