import { Link, useParams } from 'react-router'
import { ChevronLeftIcon, InfoIcon } from 'lucide-react'

import { ApiErrorAlert } from '@/components/api-error-alert'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Skeleton } from '@/components/ui/skeleton'
import { useEvQuery } from '@/features/ev/queries'
import { ScheduleStatusBadge } from '@/features/charging/components/schedule-status-badge'
import { ChargingSummaryCard } from '@/features/charging/components/charging-summary-card'
import { useChargingScheduleQuery } from '@/features/charging/queries'
import { formatDateTime } from '@/lib/format'

export function ChargingScheduleDetailPage() {
  const { scheduleId } = useParams()
  const id = Number(scheduleId)
  const { data: schedule, isPending, isError, error } = useChargingScheduleQuery(id)
  const { data: ev } = useEvQuery(schedule?.evId ?? Number.NaN)

  return (
    <div className="space-y-6">
      <Link
        to="/charging/schedules"
        className="text-muted-foreground hover:text-foreground inline-flex items-center gap-1 text-sm"
      >
        <ChevronLeftIcon className="size-4" /> Charging schedules
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

          {schedule.status === 'CREATED' ? (
            <Alert>
              <InfoIcon />
              <AlertTitle>Charging is scheduled</AlertTitle>
              <AlertDescription>
                Your EV will charge during the window below. Running the charging session is a later
                step.
              </AlertDescription>
            </Alert>
          ) : null}

          <ChargingSummaryCard
            title="Window & cost"
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
        </>
      ) : null}
    </div>
  )
}
