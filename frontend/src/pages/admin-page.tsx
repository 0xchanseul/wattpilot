import { useMemo, useState } from 'react'
import { GlobeIcon, LogOutIcon, RouteIcon, TrendingUpIcon } from 'lucide-react'

import { ApiErrorAlert } from '@/components/api-error-alert'
import { FullPageSpinner } from '@/components/full-page-spinner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { AdminLoginForm } from '@/features/admin/components/admin-login-form'
import { AdminSummaryCards } from '@/features/admin/components/admin-summary-cards'
import { BreakdownBars } from '@/features/admin/components/breakdown-bars'
import { DailyTrendChart } from '@/features/admin/components/daily-trend-chart'
import { RangeFilter } from '@/features/admin/components/range-filter'
import {
  useAdminLogoutMutation,
  useAdminSessionQuery,
  useAnalyticsQuery,
} from '@/features/admin/queries'
import { presetRange, type RangePreset } from '@/features/admin/range'
import { sourceLabel } from '@/features/admin/source-label'
import { ApiError } from '@/lib/api-error'

function isUnauthorized(error: unknown): boolean {
  return error instanceof ApiError && error.status === 401
}

export function AdminPage() {
  const session = useAdminSessionQuery()

  return (
    <>
      {/* Not a security measure; it only keeps the page out of search results. */}
      <meta name="robots" content="noindex" />
      {session.isPending ? (
        <FullPageSpinner />
      ) : session.isError ? (
        isUnauthorized(session.error) ? (
          <AdminLoginForm />
        ) : (
          <div className="mx-auto max-w-md px-4 py-10">
            <ApiErrorAlert error={session.error} title="Could not reach the server" />
          </div>
        )
      ) : (
        <Dashboard />
      )}
    </>
  )
}

function Dashboard() {
  const [preset, setPreset] = useState<RangePreset>('7d')
  const range = useMemo(() => presetRange(preset), [preset])
  const analytics = useAnalyticsQuery(range, true)
  const logout = useAdminLogoutMutation()

  // The cookie can expire while the page stays open; the next request then answers 401.
  if (isUnauthorized(analytics.error)) {
    return <AdminLoginForm />
  }

  const data = analytics.data

  return (
    <div className="mx-auto max-w-6xl space-y-6 px-4 py-6">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Admin</h1>
          <p className="text-muted-foreground text-sm">Visitors, sign-ups and charging activity</p>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <RangeFilter value={preset} onChange={setPreset} />
          <Button variant="outline" size="sm" onClick={() => logout.mutate()} disabled={logout.isPending}>
            <LogOutIcon /> Log out
          </Button>
        </div>
      </header>

      {analytics.isPending ? (
        <div className="space-y-4">
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {Array.from({ length: 4 }).map((_, index) => (
              <Skeleton key={index} className="h-24 w-full" />
            ))}
          </div>
          <Skeleton className="h-72 w-full" />
        </div>
      ) : null}

      {analytics.isError ? (
        <div className="space-y-3">
          <ApiErrorAlert error={analytics.error} title="Could not load the statistics" />
          <Button variant="outline" onClick={() => void analytics.refetch()}>
            Try again
          </Button>
        </div>
      ) : null}

      {data ? (
        <>
          <AdminSummaryCards summary={data.summary} />

          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <TrendingUpIcon className="text-muted-foreground size-4" />
                Daily trend
              </CardTitle>
            </CardHeader>
            <CardContent>
              <DailyTrendChart points={data.daily} />
            </CardContent>
          </Card>

          <div className="grid gap-6 lg:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <RouteIcon className="text-muted-foreground size-4" />
                  Traffic sources
                </CardTitle>
              </CardHeader>
              <CardContent>
                <BreakdownBars
                  emptyText="No visitors in this range."
                  rows={data.sources.map((row) => ({
                    key: row.source ?? '__unknown',
                    label: sourceLabel(row.source),
                    visitors: row.visitors,
                    share: row.share,
                  }))}
                />
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <GlobeIcon className="text-muted-foreground size-4" />
                  Countries
                </CardTitle>
              </CardHeader>
              <CardContent>
                <BreakdownBars
                  emptyText="No visitors in this range."
                  rows={data.countries.map((row) => ({
                    key: row.countryCode ?? '__unknown',
                    label: row.name,
                    visitors: row.visitors,
                    share: row.share,
                  }))}
                />
              </CardContent>
            </Card>
          </div>

          <p className="text-muted-foreground text-xs">
            A visitor is counted once per day, so someone who returns on three days counts three times.
            Sign-up conversion divides sign-ups by those visitor-days, so it understates the real rate.
            Sign-ups and schedules exclude demo accounts and the demo template account, and fall when an
            account is deleted.
          </p>
        </>
      ) : null}
    </div>
  )
}
