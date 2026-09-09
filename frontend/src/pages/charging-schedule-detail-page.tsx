import { Link, useParams } from 'react-router'
import { ChevronLeftIcon, InfoIcon } from 'lucide-react'

import { ApiErrorAlert } from '@/components/api-error-alert'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useEvQuery } from '@/features/ev/queries'
import { ScheduleStatusBadge } from '@/features/charging/components/schedule-status-badge'
import { ScheduleTimeline } from '@/features/charging/components/schedule-timeline'
import { ChargingSummaryCard } from '@/features/charging/components/charging-summary-card'
import { useChargingScheduleQuery } from '@/features/charging/queries'
import { chargingFailureText } from '@/features/charging/failure-copy'
import type { ChargingSchedule, ChargingSessionSummary } from '@/features/charging/types'
import { cn } from '@/lib/utils'
import { useBackTarget } from '@/lib/navigation'
import { formatDateTime, formatKwh, formatNok } from '@/lib/format'

export function ChargingScheduleDetailPage() {
  const { scheduleId } = useParams()
  const id = Number(scheduleId)
  const { data: schedule, isPending, isError, error } = useChargingScheduleQuery(id)
  const { data: ev } = useEvQuery(schedule?.evId ?? Number.NaN)
  const back = useBackTarget({ to: '/charging/schedules', label: 'Charging schedules' })

  return (
    <div className="space-y-6">
      <Link
        to={back.to}
        className="text-muted-foreground hover:text-foreground inline-flex items-center gap-1 text-sm"
      >
        <ChevronLeftIcon className="size-4" /> {back.label}
      </Link>

      {isPending ? <Skeleton className="h-96 w-full" /> : null}
      {isError ? <ApiErrorAlert error={error} title="Could not load this schedule" /> : null}

      {schedule ? (
        <>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <h1 className="text-2xl font-semibold">Charging schedule</h1>
              <p className="text-muted-foreground text-sm">
                Created {formatDateTime(schedule.createdAt)} · plan #{schedule.planId}
              </p>
            </div>
            <ScheduleStatusBadge status={schedule.status} />
          </div>

          <StatusBanner schedule={schedule} />

          <Card>
            <CardHeader>
              <CardTitle>Progress</CardTitle>
            </CardHeader>
            <CardContent>
              <ScheduleTimeline schedule={schedule} />
            </CardContent>
          </Card>

          <ChargingSummaryCard
            title="Planned window"
            evName={ev?.name}
            startAt={schedule.scheduledStartAt}
            endAt={schedule.scheduledEndAt}
            calculatedEnergyKwh={schedule.calculatedEnergyKwh}
            expectedEnergyKwh={schedule.expectedEnergyKwh}
            estimatedCostNok={schedule.estimatedCostNok}
            baselineCostNok={schedule.baselineCostNok}
            expectedSavingsNok={schedule.expectedSavingsNok}
            slots={schedule.slots}
          />

          {schedule.session && schedule.status === 'COMPLETED' ? (
            <ChargingResultCard session={schedule.session} />
          ) : null}
        </>
      ) : null}
    </div>
  )
}

function StatusBanner({ schedule }: { schedule: ChargingSchedule }) {
  switch (schedule.status) {
    case 'FAILED':
      return (
        <p className="text-destructive text-sm">
          {chargingFailureText(
            schedule.session?.failureCode ?? null,
            schedule.session?.failureReason ?? null,
          ) ?? 'Charging did not complete.'}
        </p>
      )
    case 'CREATED':
    case 'WAITING':
      return (
        <Alert>
          <InfoIcon />
          <AlertTitle>Charging is scheduled</AlertTitle>
          <AlertDescription>
            Your EV will charge during the window below. Running the charging session is a later
            step.
          </AlertDescription>
        </Alert>
      )
    case 'IN_PROGRESS':
      return (
        <Alert>
          <InfoIcon />
          <AlertTitle>Charging now</AlertTitle>
          <AlertDescription>This schedule is charging during the window below.</AlertDescription>
        </Alert>
      )
    case 'CANCELLED':
      return <p className="text-muted-foreground text-sm">This schedule was cancelled.</p>
    default:
      return null
  }
}

/**
 * The realized outcome of this schedule's Mock Charging run. This is the schedule-centric view of
 * the result; the full receipt (conditions snapshot and the optimized plan) lives in charging history.
 */
function ChargingResultCard({ session }: { session: ChargingSessionSummary }) {
  const realizedSavingsNok =
    session.baselineCostNok != null && session.actualCostNok != null
      ? session.baselineCostNok - session.actualCostNok
      : null

  return (
    <Card>
      <CardHeader>
        <CardTitle>Charging result</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <dl className="grid grid-cols-2 gap-x-6 gap-y-4 sm:grid-cols-3">
          <Fact
            label="Charged"
            value={
              session.startedAt && session.completedAt
                ? `${formatDateTime(session.startedAt)} → ${formatDateTime(session.completedAt)}`
                : '—'
            }
          />
          <Fact
            label="Energy delivered"
            value={session.actualEnergyKwh != null ? formatKwh(session.actualEnergyKwh) : '—'}
          />
          <Fact
            label="Actual cost"
            value={session.actualCostNok != null ? formatNok(session.actualCostNok) : '—'}
          />
          <div className="space-y-0.5">
            <dt className="text-muted-foreground text-xs">You saved</dt>
            <dd
              className={cn(
                'text-lg font-semibold',
                (realizedSavingsNok ?? 0) > 0 && 'text-chart-2',
              )}
            >
              {realizedSavingsNok != null ? formatNok(realizedSavingsNok) : '—'}
            </dd>
            <p className="text-muted-foreground text-xs">vs. charging at the typical-time price</p>
          </div>
        </dl>
        <p className="text-muted-foreground text-xs">
          The full record, including the charging conditions and the optimized plan, is in your
          charging history.
        </p>
      </CardContent>
    </Card>
  )
}

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <div className="space-y-0.5">
      <dt className="text-muted-foreground text-xs">{label}</dt>
      <dd className="font-medium">{value}</dd>
    </div>
  )
}
