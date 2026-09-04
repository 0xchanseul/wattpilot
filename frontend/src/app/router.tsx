import { createBrowserRouter } from 'react-router'

import { AppLayout } from '@/components/layout/app-layout'
import { ProtectedRoute, PublicOnlyRoute, RootRedirect } from '@/components/route-guards'
import { LoginPage } from '@/pages/login-page'
import { SignUpPage } from '@/pages/sign-up-page'
import { EvListPage } from '@/pages/ev-list-page'
import { EvCreatePage } from '@/pages/ev-create-page'
import { EvDetailPage } from '@/pages/ev-detail-page'
import { EvEditPage } from '@/pages/ev-edit-page'
import { ChargingPlanNewPage } from '@/pages/charging-plan-new-page'
import { ChargingSchedulesPage } from '@/pages/charging-schedules-page'
import { ChargingScheduleDetailPage } from '@/pages/charging-schedule-detail-page'
import { NotFoundPage } from '@/pages/not-found-page'

export const router = createBrowserRouter([
  { index: true, element: <RootRedirect /> },
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
          { path: 'evs', element: <EvListPage /> },
          { path: 'evs/new', element: <EvCreatePage /> },
          { path: 'evs/:evId', element: <EvDetailPage /> },
          { path: 'evs/:evId/edit', element: <EvEditPage /> },
          { path: 'charging/new', element: <ChargingPlanNewPage /> },
          { path: 'charging/schedules', element: <ChargingSchedulesPage /> },
          { path: 'charging/schedules/:scheduleId', element: <ChargingScheduleDetailPage /> },
        ],
      },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
])
