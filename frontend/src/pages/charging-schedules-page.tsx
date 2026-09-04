import { useMemo } from 'react'
import { Link } from 'react-router'
import { CalendarClockIcon, PlusIcon } from 'lucide-react'

import { ApiErrorAlert } from '@/components/api-error-alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useEvsQuery } from '@/features/ev/queries'
import { ScheduleStatusBadge } from '@/features/charging/components/schedule-status-badge'
import { useChargingSchedulesQuery } from '@/features/charging/queries'
import type { ChargingSchedule } from '@/features/charging/types'
import { formatDateTime, formatNok } from '@/lib/format'

const PAGE_SIZE = 100

export function ChargingSchedulesPage() {
  const { data, isPending, isError, error, refetch } = useChargingSchedulesQuery({ size: PAGE_SIZE })
  const activeEvs = useEvsQuery({ size: PAGE_SIZE })
  const inactiveEvs = useEvsQuery({ status: 'INACTIVE', size: PAGE_SIZE })

  const evNameById = useMemo(() => {
    const map = new Map<number, string>()
    for (const ev of [...(activeEvs.data?.content ?? []), ...(inactiveEvs.data?.content ?? [])]) {
      map.set(ev.id, ev.name)
    }
    return map
  }, [activeEvs.data, inactiveEvs.data])

  return (
    <div className="space-y-6">
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

      {data && data.content.length === 0 ? <EmptyState /> : null}

      {data && data.content.length > 0 ? (
        <ul className="grid gap-4 sm:grid-cols-2">
          {data.content.map((schedule) => (
            <li key={schedule.id}>
              <ScheduleCard
                schedule={schedule}
                evName={evNameById.get(schedule.evId) ?? `EV #${schedule.evId}`}
              />
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  )
}

function ScheduleCard({ schedule, evName }: { schedule: ChargingSchedule; evName: string }) {
  return (
    <Link to={`/charging/schedules/${schedule.id}`} className="block h-full">
      <Card className="hover:border-ring h-full gap-3 transition-colors">
        <CardHeader>
          <div className="flex items-start justify-between gap-2">
            <CardTitle>{evName}</CardTitle>
            <ScheduleStatusBadge status={schedule.status} />
          </div>
          <p className="text-muted-foreground text-sm">
            {formatDateTime(schedule.scheduledStartAt)} → {formatDateTime(schedule.scheduledEndAt)}
          </p>
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
