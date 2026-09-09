import type { ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router'
import {
  ActivityIcon,
  CarIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
  TrendingUpIcon,
  ZapIcon,
} from 'lucide-react'

import { ApiErrorAlert } from '@/components/api-error-alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { chargingFailureText } from '@/features/charging/failure-copy'
import { HistoryStatusBadge } from '@/features/history/components/history-status-badge'
import { useChargingHistoryQuery } from '@/features/history/queries'
import type {
  ChargingHistoryItem,
  ChargingHistorySummary,
  HistoryStatus,
} from '@/features/history/types'
import { cn } from '@/lib/utils'
import { formatDateTime, formatKwh, formatNok } from '@/lib/format'

const PAGE_SIZE = 20

const STATUS_FILTERS: { label: string; value: HistoryStatus | 'ALL' }[] = [
  { label: 'All', value: 'ALL' },
  { label: 'Completed', value: 'COMPLETED' },
  { label: 'Failed', value: 'FAILED' },
]

export function ChargingHistoryPage() {
  const [searchParams, setSearchParams] = useSearchParams()

  const page = Math.max(1, Number(searchParams.get('page')) || 1)
  const statusParam = searchParams.get('status')
  const status: HistoryStatus | undefined =
    statusParam === 'COMPLETED' || statusParam === 'FAILED' ? statusParam : undefined

  const { data, isPending, isFetching, isError, error, refetch } = useChargingHistoryQuery({
    page: page - 1,
    size: PAGE_SIZE,
    status,
  })

  const setPage = (next: number) => {
    setSearchParams((prev) => {
      const params = new URLSearchParams(prev)
      if (next <= 1) params.delete('page')
      else params.set('page', String(next))
      return params
    })
  }

  const setStatus = (next: HistoryStatus | 'ALL') => {
    setSearchParams((prev) => {
      const params = new URLSearchParams(prev)
      if (next === 'ALL') params.delete('status')
      else params.set('status', next)
      params.delete('page')
      return params
    })
  }

  const meta = data?.page
  const hasHistory = (data?.summary.totalSessions ?? 0) > 0

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Charging history</h1>
        <p className="text-muted-foreground text-sm">
          Every completed and failed charge, and what you saved.
        </p>
      </div>

      {isPending ? (
        <div className="space-y-4">
          <Skeleton className="h-24 w-full" />
          {Array.from({ length: 4 }).map((_, index) => (
            <Skeleton key={index} className="h-20 w-full" />
          ))}
        </div>
      ) : null}

      {isError ? (
        <div className="space-y-3">
          <ApiErrorAlert error={error} title="Could not load your charging history" />
          <Button variant="outline" onClick={() => refetch()}>
            Try again
          </Button>
        </div>
      ) : null}

      {data && !hasHistory ? <EmptyState /> : null}

      {data && hasHistory ? (
        <>
          <SummaryStats summary={data.summary} />

          <div className="flex flex-wrap gap-1">
            {STATUS_FILTERS.map((filter) => {
              const active =
                filter.value === 'ALL' ? status === undefined : status === filter.value
              return (
                <Button
                  key={filter.value}
                  size="sm"
                  variant={active ? 'default' : 'outline'}
                  onClick={() => setStatus(filter.value)}
                >
                  {filter.label}
                </Button>
              )
            })}
          </div>

          {data.content.length === 0 ? (
            <p className="text-muted-foreground py-8 text-center text-sm">
              No charges match this filter.
            </p>
          ) : (
            <ul className={cn('space-y-3', isFetching && 'opacity-60')}>
              {data.content.map((item) => (
                <li key={item.sessionId}>
                  <HistoryRow item={item} />
                </li>
              ))}
            </ul>
          )}

          {meta && meta.totalPages > 1 ? (
            <div className="flex items-center justify-between">
              <Button
                variant="outline"
                size="sm"
                disabled={meta.first || isFetching}
                onClick={() => setPage(page - 1)}
              >
                <ChevronLeftIcon /> Previous
              </Button>
              <span className="text-muted-foreground text-sm tabular-nums">
                Page {meta.page + 1} of {meta.totalPages}
              </span>
              <Button
                variant="outline"
                size="sm"
                disabled={meta.last || isFetching}
                onClick={() => setPage(page + 1)}
              >
                Next <ChevronRightIcon />
              </Button>
            </div>
          ) : null}
        </>
      ) : null}
    </div>
  )
}

function SummaryStats({ summary }: { summary: ChargingHistorySummary }) {
  return (
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
      <Card className="border-chart-2/40 bg-chart-2/5">
        <CardContent className="space-y-1">
          <p className="text-muted-foreground flex items-center gap-1.5 text-xs">
            <TrendingUpIcon className="size-3.5" /> Total saved
          </p>
          <p className="text-chart-2 text-2xl font-semibold tabular-nums">
            {formatNok(summary.totalSavingsNok)}
          </p>
        </CardContent>
      </Card>
      <Stat label="Energy charged" value={formatKwh(summary.totalEnergyKwh)} icon={<ZapIcon className="size-3.5" />} />
      <Stat label="Charges" value={String(summary.totalSessions)} icon={<ActivityIcon className="size-3.5" />} />
      <Stat label="Success rate" value={`${summary.successRate}%`} icon={<ActivityIcon className="size-3.5" />} />
    </div>
  )
}

function Stat({ label, value, icon }: { label: string; value: string; icon: ReactNode }) {
  return (
    <Card>
      <CardContent className="space-y-1">
        <p className="text-muted-foreground flex items-center gap-1.5 text-xs">
          {icon} {label}
        </p>
        <p className="text-xl font-semibold tabular-nums">{value}</p>
      </CardContent>
    </Card>
  )
}

function HistoryRow({ item }: { item: ChargingHistoryItem }) {
  const when = formatDateTime(item.completedAt ?? item.recordedAt)

  return (
    <Link to={`/charging/history/${item.sessionId}`} className="block">
      <Card className="hover:border-ring transition-colors">
        <CardContent className="flex flex-wrap items-center justify-between gap-x-6 gap-y-2">
          <div className="space-y-1">
            <div className="flex items-center gap-2 text-sm font-medium">
              <CarIcon className="size-4 shrink-0" />
              {item.evName}
              <HistoryStatusBadge status={item.status} />
            </div>
            <p className="text-muted-foreground text-xs">{when}</p>
          </div>

          {item.status === 'COMPLETED' ? (
            <dl className="flex gap-5 text-sm tabular-nums">
              <div className="text-right">
                <dt className="text-muted-foreground text-xs">Energy</dt>
                <dd>{item.actualEnergyKwh != null ? formatKwh(item.actualEnergyKwh) : '—'}</dd>
              </div>
              <div className="text-right">
                <dt className="text-muted-foreground text-xs">Cost</dt>
                <dd>{item.actualCostNok != null ? formatNok(item.actualCostNok) : '—'}</dd>
              </div>
              <div className="text-right">
                <dt className="text-muted-foreground text-xs">Saved</dt>
                <dd className="text-chart-2">
                  {item.realizedSavingsNok != null ? formatNok(item.realizedSavingsNok) : '—'}
                </dd>
              </div>
            </dl>
          ) : (
            <p className="text-destructive max-w-xs text-sm">
              {chargingFailureText(item.failureCode, item.failureReason) ??
                'Charging did not complete.'}
            </p>
          )}
        </CardContent>
      </Card>
    </Link>
  )
}

function EmptyState() {
  return (
    <Card>
      <CardContent className="flex flex-col items-center gap-3 py-12 text-center">
        <div className="bg-secondary flex size-12 items-center justify-center rounded-full">
          <ActivityIcon className="text-muted-foreground size-6" />
        </div>
        <div>
          <p className="font-medium">No charging history yet</p>
          <p className="text-muted-foreground text-sm">
            Once a scheduled charge runs, its result and savings show up here.
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
