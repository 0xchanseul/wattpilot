import { useQuery } from '@tanstack/react-query'

import { getDashboard } from './api'

export const dashboardKeys = {
  all: ['dashboard'] as const,
}

export function useDashboardQuery() {
  return useQuery({
    queryKey: dashboardKeys.all,
    queryFn: getDashboard,
  })
}
