import { createBrowserRouter } from 'react-router'

import { AppLayout } from '@/components/layout/app-layout'
import { ProtectedRoute, PublicOnlyRoute, RootRedirect } from '@/components/route-guards'
import { DashboardPage } from '@/pages/dashboard-page'
import { LoginPage } from '@/pages/login-page'
import { SignUpPage } from '@/pages/sign-up-page'
import { EvListPage } from '@/pages/ev-list-page'
import { EvCreatePage } from '@/pages/ev-create-page'
import { EvDetailPage } from '@/pages/ev-detail-page'
import { EvEditPage } from '@/pages/ev-edit-page'
import { ChargingPlanNewPage } from '@/pages/charging-plan-new-page'
import { ChargingSchedulesPage } from '@/pages/charging-schedules-page'
import { ChargingScheduleDetailPage } from '@/pages/charging-schedule-detail-page'
import { ChargingHistoryPage } from '@/pages/charging-history-page'
import { ChargingHistoryDetailPage } from '@/pages/charging-history-detail-page'
import { SavingsPage } from '@/pages/savings-page'
import { ProfilePage } from '@/pages/profile-page'
import { VehicleConnectionCallbackPage } from '@/pages/vehicle-connection-callback-page'
import { NotFoundPage } from '@/pages/not-found-page'
import { PrivacyPage } from '@/pages/privacy-page'

export const router = createBrowserRouter([
  { index: true, element: <RootRedirect /> },
  // Public for everyone, signed in or not: the policy is linked from sign-up and from the profile.
  { path: 'privacy', element: <PrivacyPage /> },
  // Operator-only statistics; not linked from anywhere. Loaded on demand so no visitor downloads it.
  {
    path: 'admin',
    lazy: async () => ({ Component: (await import('@/pages/admin-page')).AdminPage }),
  },
  {
    element: <PublicOnlyRoute />,
    children: [
      { path: 'login', element: <LoginPage /> },
      { path: 'signup', element: <SignUpPage /> },
    ],
  },
  {
    element: <ProtectedRoute />,
    children: [
      {
        element: <AppLayout />,
        children: [
          { path: 'dashboard', element: <DashboardPage /> },
          { path: 'evs', element: <EvListPage /> },
          { path: 'evs/new', element: <EvCreatePage /> },
          { path: 'evs/:evId', element: <EvDetailPage /> },
          { path: 'evs/:evId/edit', element: <EvEditPage /> },
          { path: 'charging/new', element: <ChargingPlanNewPage /> },
          { path: 'charging/schedules', element: <ChargingSchedulesPage /> },
          { path: 'charging/schedules/:scheduleId', element: <ChargingScheduleDetailPage /> },
          { path: 'charging/history', element: <ChargingHistoryPage /> },
          { path: 'charging/history/:sessionId', element: <ChargingHistoryDetailPage /> },
          { path: 'savings', element: <SavingsPage /> },
          { path: 'profile', element: <ProfilePage /> },
          { path: 'vehicle-connections/callback', element: <VehicleConnectionCallbackPage /> },
        ],
      },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
])
