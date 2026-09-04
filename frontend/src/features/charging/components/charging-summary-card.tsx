import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { cn } from '@/lib/utils'
import {
  formatDateTime,
  formatDurationMinutes,
  formatKwh,
  formatNok,
} from '@/lib/format'
import { priceAreaLabel } from '@/lib/price-area'
import type { PriceArea } from '@/types/api'
import type { ElectricityPrice } from '@/features/electricity/types'
import type { ChargingPlanSlot } from '../types'
import { PriceTimeline } from './price-timeline'
import { SlotTable } from './slot-table'

export interface ChargingSummaryCardProps {
  title: string
  evName?: string
  priceArea?: PriceArea
  startAt: string
  endAt: string
  /** Preview supplies this; otherwise it is derived from start/end. */
  durationMinutes?: number
  batteryFromPercent?: number
  batteryToPercent?: number
  /** Battery-side energy to add. */
  calculatedEnergyKwh?: number
  /** Grid-side energy drawn (includes efficiency loss). */
  expectedEnergyKwh: number
  estimatedCostNok: number
  baselineCostNok: number
  expectedSavingsNok: number
  slots: ChargingPlanSlot[]
  /** Hourly prices for the timeline; omitted when unavailable. */
  prices?: ElectricityPrice[]
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

export function ChargingSummaryCard({
  title,
  evName,
  priceArea,
  startAt,
  endAt,
  durationMinutes,
  batteryFromPercent,
  batteryToPercent,
  calculatedEnergyKwh,
  expectedEnergyKwh,
  estimatedCostNok,
  baselineCostNok,
  expectedSavingsNok,
  slots,
  prices,
}: ChargingSummaryCardProps) {
  const minutes =
    durationMinutes ?? Math.round((new Date(endAt).getTime() - new Date(startAt).getTime()) / 60000)
  const saves = expectedSavingsNok > 0
  const showBattery =
    batteryFromPercent !== undefined && batteryToPercent !== undefined

  return (
    <Card>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-6">
        {evName ? (
          <p className="text-sm">
            Charging <span className="font-medium">{evName}</span>
            {priceArea ? (
              <span className="text-muted-foreground"> · {priceAreaLabel(priceArea)}</span>
            ) : null}
          </p>
        ) : null}

        <dl className="grid grid-cols-2 gap-x-6 gap-y-4 sm:grid-cols-3">
          <Fact label="Starts" value={formatDateTime(startAt)} />
          <Fact label="Finishes" value={formatDateTime(endAt)} />
          <Fact label="Charging time" value={formatDurationMinutes(minutes)} />
          {showBattery ? (
            <Fact
              label="Battery"
              value={`${batteryFromPercent}% → ${batteryToPercent}%`}
            />
          ) : null}
          <Fact
            label="Energy"
            value={formatKwh(expectedEnergyKwh)}
            hint={
              calculatedEnergyKwh !== undefined
                ? `${formatKwh(calculatedEnergyKwh)} to the battery`
                : undefined
            }
          />
          <Fact label="Estimated cost" value={formatNok(estimatedCostNok)} />
          <Fact label="If charged now" value={formatNok(baselineCostNok)} />
          <div className="space-y-0.5">
            <dt className="text-muted-foreground text-xs">Estimated savings</dt>
            <dd className={cn('font-medium', saves && 'text-chart-2')}>
              {formatNok(expectedSavingsNok)}
            </dd>
          </div>
        </dl>

        {prices && prices.length > 0 ? (
          <div className="space-y-2">
            <p className="text-sm font-medium">Hourly price &amp; selected window</p>
            <PriceTimeline prices={prices} windowStartAt={startAt} windowEndAt={endAt} />
          </div>
        ) : null}

        <div className="space-y-2">
          <p className="text-sm font-medium">Selected price slots</p>
          <SlotTable slots={slots} />
          <p className="text-muted-foreground text-xs">
            MVP charging uses a single continuous window, so the slots above are consecutive.
          </p>
        </div>
      </CardContent>
    </Card>
  )
}
