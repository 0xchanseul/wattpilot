import { Link, useParams } from 'react-router'
import { ChevronLeftIcon } from 'lucide-react'

import { ApiErrorAlert } from '@/components/api-error-alert'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { ChargingSummaryCard } from '@/features/charging/components/charging-summary-card'
import { chargingFailureText } from '@/features/charging/failure-copy'
import { HistoryStatusBadge } from '@/features/history/components/history-status-badge'
import { useChargingHistoryEntryQuery } from '@/features/history/queries'
import type { ChargingHistoryDetail } from '@/features/history/types'
import { cn } from '@/lib/utils'
import { formatDateTime, formatKw, formatKwh, formatNok } from '@/lib/format'
import { priceAreaLabel } from '@/lib/price-area'

export function ChargingHistoryDetailPage() {
  const { sessionId } = useParams()
  const id = Number(sessionId)
  const { data, isPending, isError, error } = useChargingHistoryEntryQuery(id)

  return (
    <div className="space-y-6">
      <Link
        to="/charging/history"
        className="text-muted-foreground hover:text-foreground inline-flex items-center gap-1 text-sm"
      >
        <ChevronLeftIcon className="size-4" /> Charging history
      </Link>

      {isPending ? <Skeleton className="h-96 w-full" /> : null}
      {isError ? <ApiErrorAlert error={error} title="Could not load this charging record" /> : null}

      {data ? <Receipt detail={data} /> : null}
    </div>
  )
}

function Receipt({ detail }: { detail: ChargingHistoryDetail }) {
  return (
    <>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h1 className="text-2xl font-semibold">Charging result</h1>
          <p className="text-muted-foreground text-sm">
            {formatDateTime(detail.completedAt ?? detail.recordedAt)} · schedule #{detail.scheduleId}
          </p>
        </div>
        <HistoryStatusBadge status={detail.status} />
      </div>

      {detail.status === 'FAILED' ? (
        <p className="text-destructive text-sm">
          {chargingFailureText(detail.failureCode, detail.failureReason) ??
            'Charging did not complete.'}
        </p>
      ) : null}

      <Card>
        <CardHeader>
          <CardTitle>Charging conditions</CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid grid-cols-2 gap-x-6 gap-y-4 sm:grid-cols-3">
            <Fact
              label="EV"
              value={detail.evSnapshot.name}
              hint={`${detail.evSnapshot.manufacturer} ${detail.evSnapshot.model} · ${formatKwh(
                detail.evSnapshot.batteryCapacityKwh,
              )}`}
            />
            <Fact
              label="Requested charge"
              value={`${detail.startBatteryPercent}% → ${detail.targetBatteryPercent}%`}
            />
            <Fact label="Price area" value={priceAreaLabel(detail.priceArea)} />
            <Fact label="Needed by" value={formatDateTime(detail.requiredCompletionAt)} />
            <Fact label="Earliest start" value={formatDateTime(detail.earliestStartAt)} />
            <Fact label="Charging power" value={formatKw(detail.effectiveChargingPowerKw)} />
          </dl>
        </CardContent>
      </Card>

      <ChargingSummaryCard
        title="Optimized plan"
        startAt={detail.recommendedStartAt}
        endAt={detail.recommendedEndAt}
        durationMinutes={detail.estimatedDurationMinutes}
        calculatedEnergyKwh={detail.calculatedEnergyKwh}
        expectedEnergyKwh={detail.plannedEnergyKwh}
        estimatedCostNok={detail.optimizedCostNok}
        baselineCostNok={detail.baselineCostNok}
        expectedSavingsNok={detail.estimatedSavingsNok}
        slots={detail.plannedSlots}
      />

      <Card>
        <CardHeader>
          <CardTitle>Charging result</CardTitle>
        </CardHeader>
        <CardContent>
          {detail.status === 'COMPLETED' ? (
            <dl className="grid grid-cols-2 gap-x-6 gap-y-4 sm:grid-cols-3">
              <Fact
                label="Charged"
                value={
                  detail.startedAt && detail.completedAt
                    ? `${formatDateTime(detail.startedAt)} → ${formatDateTime(detail.completedAt)}`
                    : '—'
                }
              />
              <Fact
                label="Energy delivered"
                value={detail.actualEnergyKwh != null ? formatKwh(detail.actualEnergyKwh) : '—'}
              />
              <Fact
                label="Actual cost"
                value={detail.actualCostNok != null ? formatNok(detail.actualCostNok) : '—'}
              />
              <div className="space-y-0.5">
                <dt className="text-muted-foreground text-xs">You saved</dt>
                <dd
                  className={cn(
                    'text-lg font-semibold',
                    (detail.realizedSavingsNok ?? 0) > 0 && 'text-chart-2',
                  )}
                >
                  {detail.realizedSavingsNok != null ? formatNok(detail.realizedSavingsNok) : '—'}
                </dd>
                <p className="text-muted-foreground text-xs">vs. charging at the typical-time price</p>
              </div>
            </dl>
          ) : (
            <p className="text-muted-foreground text-sm">
              This charge did not run, so there is no delivered energy, cost or saving to show. The
              plan above is what would have happened.
            </p>
          )}
        </CardContent>
      </Card>
    </>
  )
}

function Fact({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className="space-y-0.5">
      <dt className="text-muted-foreground text-xs">{label}</dt>
      <dd className="font-medium">{value}</dd>
      {hint ? <p className="text-muted-foreground text-xs">{hint}</p> : null}
    </div>
  )
}
