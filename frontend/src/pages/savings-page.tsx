import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { BarChart3Icon, CalendarDaysIcon, PercentIcon, TrendingUpIcon, ZapIcon } from 'lucide-react'

import { ApiErrorAlert } from '@/components/api-error-alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useEvsQuery } from '@/features/ev/queries'
import { DailySavingsChart } from '@/features/savings/components/daily-savings-chart'
import { MonthlySavingsChart } from '@/features/savings/components/monthly-savings-chart'
import { MonthlySavingsRateChart } from '@/features/savings/components/monthly-savings-rate-chart'
import { SavingsFilters, type RangePreset } from '@/features/savings/components/savings-filters'
import { SavingsPatternChart } from '@/features/savings/components/savings-pattern-chart'
import { SavingsSummaryCards } from '@/features/savings/components/savings-summary-cards'
import { useDailySavingsQuery, useSavingsPatternsQuery, useSavingsSummaryQuery } from '@/features/savings/queries'
import type { PatternGroupBy } from '@/features/savings/types'
import { isoDateMinus, todayIso } from '@/lib/format'

const DEFAULT_PRESET: RangePreset = '6m'

function presetRange(preset: RangePreset): { from: string; to: string } {
  const to = todayIso()
  switch (preset) {
    case '30d':
      return { from: isoDateMinus(to, 'days', 29), to }
    case '3m':
      return { from: isoDateMinus(to, 'months', 3), to }
    case '12m':
      return { from: isoDateMinus(to, 'months', 12), to }
    case '6m':
    default:
      return { from: isoDateMinus(to, 'months', 6), to }
  }
}

export function SavingsPage() {
  const [searchParams, setSearchParams] = useSearchParams()

  // Only computed once per mount so the defaults don't drift as "today" ticks over mid-session.
  const defaults = useMemo(() => presetRange(DEFAULT_PRESET), [])
  const from = searchParams.get('from') || defaults.from
  const to = searchParams.get('to') || defaults.to
  const evIdParam = searchParams.get('evId')
  const evId = evIdParam ? Number(evIdParam) : undefined

  const presetParam = searchParams.get('preset') as RangePreset | null
  const hasExplicitRange = searchParams.has('from') || searchParams.has('to')
  const activePreset = presetParam ?? (hasExplicitRange ? null : DEFAULT_PRESET)

  const { data: evPage } = useEvsQuery({ size: 100 })
  const evs = useMemo(() => evPage?.content ?? [], [evPage])

  const [patternGroupBy, setPatternGroupBy] = useState<PatternGroupBy>('WEEKDAY')

  const summaryQuery = useSavingsSummaryQuery({ from, to, evId })
  const dailyQuery = useDailySavingsQuery({ from, to, evId })
  const monthlyQuery = useDailySavingsQuery({ from, to, evId, granularity: 'MONTHLY' })
  const patternsQuery = useSavingsPatternsQuery({ from, to, evId, groupBy: patternGroupBy })

  const updateParams = (
    next: Partial<{ from: string; to: string; evId: number | undefined; preset: RangePreset | null }>,
  ) => {
    setSearchParams((prev) => {
      const params = new URLSearchParams(prev)
      if (next.from) params.set('from', next.from)
      if (next.to) params.set('to', next.to)
      if ('evId' in next) {
        if (next.evId) params.set('evId', String(next.evId))
        else params.delete('evId')
      }
      if ('preset' in next) {
        if (next.preset) params.set('preset', next.preset)
        else params.delete('preset')
      }
      return params
    })
  }

  const handlePreset = (preset: RangePreset) => {
    updateParams({ ...presetRange(preset), preset })
  }

  const isPending = summaryQuery.isPending || dailyQuery.isPending || monthlyQuery.isPending
  const isError = summaryQuery.isError || dailyQuery.isError || monthlyQuery.isError
  const error = summaryQuery.error ?? dailyQuery.error ?? monthlyQuery.error

  const retry = () => {
    void summaryQuery.refetch()
    void dailyQuery.refetch()
    void monthlyQuery.refetch()
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Savings</h1>
        <p className="text-muted-foreground text-sm">
          What you&apos;ve saved by charging at the cheapest hours, over any range.
        </p>
      </div>

      <SavingsFilters
        from={from}
        to={to}
        evId={evId}
        evs={evs}
        activePreset={activePreset}
        onPreset={handlePreset}
        onFromChange={(nextFrom) => updateParams({ from: nextFrom, preset: null })}
        onToChange={(nextTo) => updateParams({ to: nextTo, preset: null })}
        onEvChange={(nextEvId) => updateParams({ evId: nextEvId })}
      />

      {isPending ? (
        <div className="space-y-4">
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {Array.from({ length: 4 }).map((_, index) => (
              <Skeleton key={index} className="h-20 w-full" />
            ))}
          </div>
          <Skeleton className="h-64 w-full" />
        </div>
      ) : null}

      {isError ? (
        <div className="space-y-3">
          <ApiErrorAlert error={error} title="Could not load your savings" />
          <Button variant="outline" onClick={retry}>
            Try again
          </Button>
        </div>
      ) : null}

      {summaryQuery.data && dailyQuery.data && monthlyQuery.data ? (
        <div className="space-y-6">
          <SavingsSummaryCards summary={summaryQuery.data} />

          {summaryQuery.data.completedSessionCount === 0 ? (
            <EmptyState />
          ) : (
            <>
              <Card>
                <CardHeader>
                  <CardTitle className="flex items-center gap-2">
                    <BarChart3Icon className="text-muted-foreground size-4" />
                    Monthly savings
                  </CardTitle>
                  <p className="text-muted-foreground text-sm">Realized savings by calendar month</p>
                </CardHeader>
                <CardContent>
                  <MonthlySavingsChart points={monthlyQuery.data} />
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle className="flex items-center gap-2">
                    <PercentIcon className="text-muted-foreground size-4" />
                    Savings rate
                  </CardTitle>
                  <p className="text-muted-foreground text-sm">
                    Realized savings as a share of baseline cost, by calendar month — how well charging
                    is timed, independent of how much was charged
                  </p>
                </CardHeader>
                <CardContent>
                  <MonthlySavingsRateChart points={monthlyQuery.data} />
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle className="flex items-center gap-2">
                    <TrendingUpIcon className="text-muted-foreground size-4" />
                    Daily trend
                  </CardTitle>
                  <p className="text-muted-foreground text-sm">Realized savings by day</p>
                </CardHeader>
                <CardContent>
                  <DailySavingsChart points={dailyQuery.data} />
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div>
                      <CardTitle className="flex items-center gap-2">
                        <CalendarDaysIcon className="text-muted-foreground size-4" />
                        Savings by weekday / hour
                      </CardTitle>
                      <p className="text-muted-foreground text-sm">When charging has actually saved the most</p>
                    </div>
                    <div className="flex gap-1">
                      <Button
                        type="button"
                        size="sm"
                        variant={patternGroupBy === 'WEEKDAY' ? 'default' : 'outline'}
                        onClick={() => setPatternGroupBy('WEEKDAY')}
                      >
                        Weekday
                      </Button>
                      <Button
                        type="button"
                        size="sm"
                        variant={patternGroupBy === 'HOUR_OF_DAY' ? 'default' : 'outline'}
                        onClick={() => setPatternGroupBy('HOUR_OF_DAY')}
                      >
                        Hour of day
                      </Button>
                    </div>
                  </div>
                </CardHeader>
                <CardContent>
                  {patternsQuery.isPending ? (
                    <Skeleton className="h-60 w-full" />
                  ) : patternsQuery.isError ? (
                    <p className="text-muted-foreground py-14 text-center text-sm">Could not load this breakdown.</p>
                  ) : (
                    <SavingsPatternChart points={patternsQuery.data} groupBy={patternGroupBy} />
                  )}
                </CardContent>
              </Card>
            </>
          )}
        </div>
      ) : null}
    </div>
  )
}

function EmptyState() {
  return (
    <Card>
      <CardContent className="flex flex-col items-center gap-3 py-12 text-center">
        <div className="bg-secondary flex size-12 items-center justify-center rounded-full">
          <ZapIcon className="text-muted-foreground size-6" />
        </div>
        <div>
          <p className="font-medium">No savings in this range yet</p>
          <p className="text-muted-foreground text-sm">
            Try a wider range, or check back once a scheduled charge completes.
          </p>
        </div>
        <Button asChild>
          <Link to="/charging/new">
            <ZapIcon /> Plan charging
          </Link>
        </Button>
      </CardContent>
    </Card>
  )
}
