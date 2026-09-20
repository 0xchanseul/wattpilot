import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { BatteryChargingIcon, CalendarClockIcon, CarIcon, PlusIcon, ZapIcon } from 'lucide-react'

import evChargingImage from '@/assets/ev-charging.png'
import evIdleImage from '@/assets/ev-idle.png'
import { Button } from '@/components/ui/button'
import { ScheduleStatusBadge } from '@/features/charging/components/schedule-status-badge'
import { formatDurationMinutes, formatKwh, formatNok, formatScheduleWindow } from '@/lib/format'
import { listOrigin } from '@/lib/navigation'
import type { DashboardNextCharging } from '../types'

const DASHBOARD_ORIGIN = listOrigin('/dashboard', 'Dashboard')

/** Bright cyan-to-teal glow, top-right to bottom-left — the reference car render's backdrop. */
const CARD_SURFACE = 'bg-gradient-to-bl from-[#eafffb] via-[#63e2d9] to-[#12a0a4]'

/**
 * The dashboard's primary hero card: whatever the EV is doing right now — charging, waiting on a
 * scheduled window, or idle — rendered on the aurora glow surface used nowhere else on the page,
 * so this stays the one bold element in the layout.
 */
export function NextChargingCard({ nextCharging }: { nextCharging: DashboardNextCharging | null }) {
  const charging = nextCharging?.status === 'IN_PROGRESS'

  if (!nextCharging) {
    return (
      <div className={`${CARD_SURFACE} relative flex h-full min-h-72 flex-col overflow-hidden rounded-xl p-6`}>
        <StatusRow label="No charging scheduled" charging={false} />
        <div className="flex flex-1 items-center justify-center py-4">
          <VehicleImage charging={false} />
        </div>
        <div className="relative space-y-3 text-center">
          <p className="text-foreground/70 text-sm">
            Plan a charge to have your EV charge when electricity is cheapest.
          </p>
          <Button asChild>
            <Link to="/charging/new">
              <PlusIcon /> Plan charging
            </Link>
          </Button>
        </div>
      </div>
    )
  }

  const window = formatScheduleWindow(nextCharging.startAt, nextCharging.endAt)
  const durationMinutes =
    (new Date(nextCharging.endAt).getTime() - new Date(nextCharging.startAt).getTime()) / 60_000

  return (
    <Link to={`/charging/schedules/${nextCharging.scheduleId}`} state={DASHBOARD_ORIGIN} className="group block h-full">
      <div className={`${CARD_SURFACE} relative flex h-full min-h-72 flex-col overflow-hidden rounded-xl p-6 transition-transform group-hover:-translate-y-0.5`}>
        <div className="flex items-start justify-between gap-2">
          <StatusRow label={charging ? 'Charging now' : 'Next charging'} charging={charging} />
          <ScheduleStatusBadge status={nextCharging.status} />
        </div>
        <p className="text-foreground/70 relative mt-1 flex items-center gap-1.5 text-sm">
          <CarIcon className="size-3.5 shrink-0" />
          {nextCharging.evName}
        </p>

        <div className="flex flex-1 items-center justify-center py-4">
          <VehicleImage charging={charging} />
        </div>

        <div className="relative space-y-4">
          <div className="space-y-1">
            <p className="text-foreground text-4xl font-semibold tabular-nums">{window.timeRange}</p>
            <p className="text-foreground/70 text-base">
              {window.date} · {formatDurationMinutes(durationMinutes)}
            </p>
          </div>
          <dl className="border-foreground/10 grid grid-cols-3 gap-x-4 gap-y-1 border-t pt-4 text-base">
            <Fact icon={<ZapIcon className="size-4" />} label="Energy" value={formatKwh(nextCharging.estimatedEnergyKwh)} />
            <Fact icon={<BatteryChargingIcon className="size-4" />} label="Cost" value={formatNok(nextCharging.estimatedCostNok)} />
            <div className="space-y-0.5">
              <dt className="text-foreground/60 flex items-center gap-1 text-sm">Savings</dt>
              <dd className="text-primary font-semibold tabular-nums sm:text-lg">{formatNok(nextCharging.estimatedSavingsNok)}</dd>
            </div>
          </dl>
        </div>
      </div>
    </Link>
  )
}

/**
 * The vehicle render assets have their black studio background keyed out and are trimmed to the
 * car, so `object-contain` inside a height cap scales the whole silhouette without cropping it.
 */
function VehicleImage({ charging }: { charging: boolean }) {
  return (
    <div className="relative w-fit max-w-[80%] min-w-0">
      <img
        src={charging ? evChargingImage : evIdleImage}
        alt={charging ? 'Vehicle charging' : 'Vehicle idle'}
        className="block max-h-48 w-auto max-w-full select-none"
        draggable={false}
      />
      {charging ? <BoltPulse /> : null}
    </div>
  )
}

/**
 * Ripples radiating from the bolt baked into the charging render. The wrapper hugs the rendered
 * image, so the percentage offsets below track the bolt at any size.
 */
function BoltPulse() {
  return (
    <span
      aria-hidden
      className="pointer-events-none absolute top-[54%] left-[67.5%] aspect-square w-[14%] -translate-x-1/2 -translate-y-1/2 motion-reduce:hidden"
    >
      {[0, 0.6].map((delay) => (
        <span
          key={delay}
          className="absolute inset-0 rounded-full border-2 border-[#7ffff0] bg-[#7ffff0]/30"
          style={{ animation: `aurora-pulse-ring 1.8s ease-out infinite ${delay}s` }}
        />
      ))}
    </span>
  )
}

function StatusRow({ label, charging }: { label: string; charging: boolean }) {
  return (
    <div className="text-foreground relative flex items-center gap-2 text-sm font-medium">
      {charging ? (
        <span className="relative flex size-2.5">
          <span className="bg-primary absolute inline-flex size-full animate-ping rounded-full opacity-75" />
          <span className="bg-primary relative inline-flex size-2.5 rounded-full" />
        </span>
      ) : (
        <CalendarClockIcon className="text-foreground/70 size-4" />
      )}
      {label}
    </div>
  )
}

function Fact({ icon, label, value }: { icon: ReactNode; label: string; value: string }) {
  return (
    <div className="space-y-0.5">
      <dt className="text-foreground/60 flex items-center gap-1 text-sm">
        {icon} {label}
      </dt>
      <dd className="text-foreground font-semibold tabular-nums sm:text-lg">{value}</dd>
    </div>
  )
}
