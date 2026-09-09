import { useMemo, type ReactNode } from 'react'
import { Link } from 'react-router'
import { CalendarClockIcon, CarIcon, ClockIcon, PlusIcon } from 'lucide-react'

import { ApiErrorAlert } from '@/components/api-error-alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useEvsQuery } from '@/features/ev/queries'
import { ScheduleStatusBadge } from '@/features/charging/components/schedule-status-badge'
import { useChargingSchedulesQuery } from '@/features/charging/queries'
import type {
  ChargingSchedule,
  ChargingScheduleRecentActivity,
} from '@/features/charging/types'
import {
  formatDurationMinutes,
  formatKwh,
  formatNok,
  formatScheduleWindow,
} from '@/lib/format'

const EV_PAGE_SIZE = 100

export function ChargingSchedulesPage() {
  const { data, isPending, isError, error, refetch } = useChargingSchedulesQuery()
  const activeEvs = useEvsQuery({ size: EV_PAGE_SIZE })
  const inactiveEvs = useEvsQuery({ status: 'INACTIVE', size: EV_PAGE_SIZE })

  const evNameById = useMemo(() => {
    const map = new Map<number, string>()
    for (const ev of [...(activeEvs.data?.content ?? []), ...(inactiveEvs.data?.content ?? [])]) {
      map.set(ev.id, ev.name)
    }
    return map
  }, [activeEvs.data, inactiveEvs.data])

  const isEmpty =
    data != null &&
    data.upcoming.length === 0 &&
    data.inProgress.length === 0 &&
    data.recentActivity.length === 0

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="text-2xl font-semibold">Charging schedules</h1>
        <Button asChild>
          <Link to="/charging/new">
            <PlusIcon /> Plan charging
          </Link>
        </Button>
      </div>

      {isPending ? (
        <div className="grid gap-4 sm:grid-cols-2">
          {Array.from({ length: 4 }).map((_, index) => (
            <Skeleton key={index} className="h-36 w-full" />
          ))}
        </div>
      ) : null}

      {isError ? (
        <div className="space-y-3">
          <ApiErrorAlert error={error} title="Could not load your schedules" />
          <Button variant="outline" onClick={() => refetch()}>
            Try again
          </Button>
        </div>
      ) : null}

      {isEmpty ? <EmptyState /> : null}

      {data && !isEmpty ? (
        <>
          {data.inProgress.length > 0 ? (
            <Section title="Charging now">
              <ScheduleGrid schedules={data.inProgress} evNameById={evNameById} />
            </Section>
          ) : null}

          {data.upcoming.length > 0 ? (
            <Section title="Upcoming">
              <ScheduleGrid schedules={data.upcoming} evNameById={evNameById} />
            </Section>
          ) : null}

          {data.recentActivity.length > 0 ? (
            <Section
              title="Recent activity"
              hint="Your last few completed and failed charges."
            >
              <ul className="grid gap-4 sm:grid-cols-2">
                {data.recentActivity.map((activity) => (
                  <li key={activity.scheduleId}>
                    <RecentActivityCard activity={activity} />
                  </li>
                ))}
              </ul>
            </Section>
          ) : null}
        </>
      ) : null}
    </div>
  )
}

function Section({
  title,
  hint,
  children,
}: {
  title: string
  hint?: string
  children: ReactNode
}) {
  return (
    <section className="space-y-3">
      <div className="space-y-0.5">
        <h2 className="text-lg font-semibold">{title}</h2>
        {hint ? <p className="text-muted-foreground text-sm">{hint}</p> : null}
      </div>
      {children}
    </section>
  )
}

function ScheduleGrid({
  schedules,
  evNameById,
}: {
  schedules: ChargingSchedule[]
  evNameById: Map<number, string>
}) {
  return (
    <ul className="grid gap-4 sm:grid-cols-2">
      {schedules.map((schedule) => (
        <li key={schedule.id}>
          <ScheduleCard
            schedule={schedule}
            evName={evNameById.get(schedule.evId) ?? `EV #${schedule.evId}`}
          />
        </li>
      ))}
    </ul>
  )
}

function ScheduleCard({ schedule, evName }: { schedule: ChargingSchedule; evName: string }) {
  const chargingWindow = formatScheduleWindow(schedule.scheduledStartAt, schedule.scheduledEndAt)
  const durationMinutes =
    (new Date(schedule.scheduledEndAt).getTime() - new Date(schedule.scheduledStartAt).getTime()) /
    60_000

  return (
    <Link to={`/charging/schedules/${schedule.id}`} className="block h-full">
      <Card className="hover:border-ring h-full gap-3 transition-colors">
        <CardHeader>
          <div className="flex items-start justify-between gap-2">
            <div className="text-muted-foreground flex items-center gap-1.5 text-sm font-medium">
              <CarIcon className="size-4 shrink-0" />
              {evName}
            </div>
            <ScheduleStatusBadge status={schedule.status} />
          </div>
          <div className="mt-1 space-y-1">
            <p className="text-foreground flex items-center gap-1.5 text-lg font-semibold tabular-nums">
              <ClockIcon className="text-muted-foreground size-4 shrink-0" />
              {chargingWindow.timeRange}
              <span className="text-muted-foreground text-sm font-normal">
                · {formatDurationMinutes(durationMinutes)}
              </span>
            </p>
            <p className="text-muted-foreground flex items-center gap-1.5 text-sm">
              <CalendarClockIcon className="size-3.5 shrink-0" />
              {chargingWindow.date}
            </p>
          </div>
        </CardHeader>
        <CardContent className="text-muted-foreground text-sm">
          <dl className="grid grid-cols-2 gap-x-4 gap-y-1">
            <dt>Estimated cost</dt>
            <dd className="text-foreground text-right">{formatNok(schedule.estimatedCostNok)}</dd>
            <dt>Estimated savings</dt>
            <dd className="text-foreground text-right">{formatNok(schedule.expectedSavingsNok)}</dd>
          </dl>
        </CardContent>
      </Card>
    </Link>
  )
}

function RecentActivityCard({ activity }: { activity: ChargingScheduleRecentActivity }) {
  const chargingWindow = formatScheduleWindow(
    activity.scheduledStartAt,
    activity.scheduledEndAt,
  )
  // A finished charge is best viewed as its history "receipt"; fall back to the schedule only if the
  // session id is somehow missing.
  const to =
    activity.sessionId != null
      ? `/charging/history/${activity.sessionId}`
      : `/charging/schedules/${activity.scheduleId}`

  return (
    <Link to={to} className="block h-full">
      <Card className="hover:border-ring h-full gap-3 transition-colors">
        <CardHeader>
          <div className="flex items-start justify-between gap-2">
            <div className="text-muted-foreground flex items-center gap-1.5 text-sm font-medium">
              <CarIcon className="size-4 shrink-0" />
              {activity.evName}
            </div>
            <ScheduleStatusBadge status={activity.status} />
          </div>
          <p className="text-muted-foreground mt-1 flex items-center gap-1.5 text-sm">
            <CalendarClockIcon className="size-3.5 shrink-0" />
            {chargingWindow.date} · {chargingWindow.timeRange}
          </p>
        </CardHeader>
        <CardContent className="text-muted-foreground text-sm">
          {activity.status === 'COMPLETED' && activity.actualEnergyKwh != null
            ? `Charged ${formatKwh(activity.actualEnergyKwh)}`
            : activity.status === 'FAILED'
              ? 'Charging did not complete'
              : null}
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
          <CalendarClockIcon className="text-muted-foreground size-6" />
        </div>
        <div>
          <p className="font-medium">No charging scheduled</p>
          <p className="text-muted-foreground text-sm">
            Plan a charging session to have your EV charge when electricity is cheapest.
          </p>
        </div>
        <Button asChild>
          <Link to="/charging/new">
            <PlusIcon /> Plan charging
          </Link>
        </Button>
      </CardContent>
    </Card>
  )
}
