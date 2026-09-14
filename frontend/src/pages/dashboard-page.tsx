import { Link } from 'react-router'
import { ArrowRightIcon, TrendingUpIcon } from 'lucide-react'

import { ApiErrorAlert } from '@/components/api-error-alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { CostComparisonCard } from '@/features/dashboard/components/cost-comparison-card'
import { CurrentPriceCard } from '@/features/dashboard/components/current-price-card'
import { DashboardSkeleton } from '@/features/dashboard/components/dashboard-skeleton'
import { NextChargingCard } from '@/features/dashboard/components/next-charging-card'
import { RecentChargingList } from '@/features/dashboard/components/recent-charging-list'
import { SavingsTrendChart } from '@/features/dashboard/components/savings-trend-chart'
import { SummaryCards } from '@/features/dashboard/components/summary-cards'
import { useDashboardQuery } from '@/features/dashboard/queries'

export function DashboardPage() {
  const { data, isPending, isError, error, refetch } = useDashboardQuery()

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Dashboard</h1>
        <p className="text-muted-foreground text-sm">
          Your charging costs, savings, and what&apos;s coming up next.
        </p>
      </div>

      {isPending ? <DashboardSkeleton /> : null}

      {isError ? (
        <div className="space-y-3">
          <ApiErrorAlert error={error} title="Could not load your dashboard" />
          <Button variant="outline" onClick={() => refetch()}>
            Try again
          </Button>
        </div>
      ) : null}

      {data ? (
        <div className="space-y-6">
          <SummaryCards summary={data.summary} />

          <div className="grid gap-4 lg:grid-cols-3">
            <div className="lg:col-span-2">
              <NextChargingCard nextCharging={data.nextCharging} />
            </div>
            <CurrentPriceCard currentPrice={data.currentPrice} />
          </div>

          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <TrendingUpIcon className="text-muted-foreground size-4" />
                Savings trend
              </CardTitle>
              <p className="text-muted-foreground text-sm">Last 30 days, realized savings</p>
            </CardHeader>
            <CardContent>
              <SavingsTrendChart trend={data.savingsTrend} />
            </CardContent>
          </Card>

          <div className="grid gap-4 lg:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle>Cost comparison</CardTitle>
                <p className="text-muted-foreground text-sm">Last 30 days</p>
              </CardHeader>
              <CardContent>
                <CostComparisonCard comparison={data.costComparison} />
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Recent charging</CardTitle>
              </CardHeader>
              <CardContent>
                <RecentChargingList sessions={data.recentSessions} />
              </CardContent>
              <CardFooter>
                <Button asChild variant="ghost" size="sm" className="w-full justify-between">
                  <Link to="/charging/history">
                    View all history
                    <ArrowRightIcon />
                  </Link>
                </Button>
              </CardFooter>
            </Card>
          </div>
        </div>
      ) : null}
    </div>
  )
}
