import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { BatteryChargingIcon, CalendarClockIcon, CarIcon, PlusIcon, ZapIcon } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { ScheduleStatusBadge } from '@/features/charging/components/schedule-status-badge'
import { formatDurationMinutes, formatKwh, formatNok, formatScheduleWindow } from '@/lib/format'
import { listOrigin } from '@/lib/navigation'
import { cn } from '@/lib/utils'
import type { DashboardNextCharging } from '../types'

const DASHBOARD_ORIGIN = listOrigin('/dashboard', 'Dashboard')

export function NextChargingCard({ nextCharging }: { nextCharging: DashboardNextCharging | null }) {
  if (!nextCharging) {
    return (
      <Card className="h-full">
        <CardContent className="flex h-full flex-col items-center justify-center gap-3 py-10 text-center">
          <div className="bg-secondary flex size-12 items-center justify-center rounded-full">
            <CalendarClockIcon className="text-muted-foreground size-6" />
          </div>
          <div>
            <p className="font-medium">No charging scheduled</p>
            <p className="text-muted-foreground text-sm">
              Plan a charge to have your EV charge when electricity is cheapest.
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

  const charging = nextCharging.status === 'IN_PROGRESS'
  const window = formatScheduleWindow(nextCharging.startAt, nextCharging.endAt)
  const durationMinutes =
    (new Date(nextCharging.endAt).getTime() - new Date(nextCharging.startAt).getTime()) / 60_000

  return (
    <Link to={`/charging/schedules/${nextCharging.scheduleId}`} state={DASHBOARD_ORIGIN} className="block h-full">
      <Card
        className={cn(
          'hover:border-ring h-full gap-4 transition-colors',
          charging && 'border-chart-2/40 bg-chart-2/5',
        )}
      >
        <CardHeader>
          <div className="flex items-start justify-between gap-2">
            <CardTitle className="flex items-center gap-2">
              {charging ? (
                <span className="relative flex size-2.5">
                  <span className="bg-chart-2 absolute inline-flex size-full animate-ping rounded-full opacity-75" />
                  <span className="bg-chart-2 relative inline-flex size-2.5 rounded-full" />
                </span>
              ) : (
                <CalendarClockIcon className="text-muted-foreground size-4" />
              )}
              {charging ? 'Charging now' : 'Next charging'}
            </CardTitle>
            <ScheduleStatusBadge status={nextCharging.status} />
          </div>
          <p className="text-muted-foreground flex items-center gap-1.5 text-sm">
            <CarIcon className="size-3.5 shrink-0" />
            {nextCharging.evName}
          </p>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-1">
            <p className="text-foreground text-lg font-semibold tabular-nums">{window.timeRange}</p>
            <p className="text-muted-foreground text-sm">
              {window.date} · {formatDurationMinutes(durationMinutes)}
            </p>
          </div>
          <dl className="grid grid-cols-3 gap-x-4 gap-y-1 text-sm">
            <Fact icon={<ZapIcon className="size-3.5" />} label="Energy" value={formatKwh(nextCharging.estimatedEnergyKwh)} />
            <Fact icon={<BatteryChargingIcon className="size-3.5" />} label="Cost" value={formatNok(nextCharging.estimatedCostNok)} />
            <div className="space-y-0.5">
              <dt className="text-muted-foreground text-xs">Savings</dt>
              <dd className="text-chart-2 font-medium tabular-nums">{formatNok(nextCharging.estimatedSavingsNok)}</dd>
            </div>
          </dl>
        </CardContent>
      </Card>
    </Link>
  )
}

function Fact({ icon, label, value }: { icon: ReactNode; label: string; value: string }) {
  return (
    <div className="space-y-0.5">
      <dt className="text-muted-foreground flex items-center gap-1 text-xs">
        {icon} {label}
      </dt>
      <dd className="font-medium tabular-nums">{value}</dd>
    </div>
  )
}
