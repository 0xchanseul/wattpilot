import { useMemo } from 'react'
import {
  Area,
  AreaChart,
  ReferenceArea,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'

import type { ElectricityPrice } from '@/features/electricity/types'
import { formatOrePerKwh, formatTime } from '@/lib/format'

interface ChargePriceChartProps {
  prices: ElectricityPrice[]
  /** The window electricity was actually drawn in. */
  chargeStartAt: string
  chargeEndAt: string
  /** The constraints the optimizer worked within. */
  earliestStartAt: string
  requiredCompletionAt: string
}

interface Point {
  t: number
  ore: number
}

/**
 * The day's hourly electricity price with the charge window shaded, so the receipt shows *why* the
 * charge landed where it did: the optimizer picked the cheapest run inside the allowed span. This
 * is history-only — the schedule response carries no price area to rebuild it from.
 */
export function ChargePriceChart({
  prices,
  chargeStartAt,
  chargeEndAt,
  earliestStartAt,
  requiredCompletionAt,
}: ChargePriceChartProps) {
  const data = useMemo<Point[]>(
    () =>
      [...prices]
        .sort((a, b) => a.startsAt.localeCompare(b.startsAt))
        .flatMap((price) => [
          { t: new Date(price.startsAt).getTime(), ore: price.pricePerKwh * 100 },
          { t: new Date(price.endsAt).getTime(), ore: price.pricePerKwh * 100 },
        ]),
    [prices],
  )

  if (data.length === 0) {
    return null
  }

  const chargeStart = new Date(chargeStartAt).getTime()
  const chargeEnd = new Date(chargeEndAt).getTime()
  const earliest = new Date(earliestStartAt).getTime()
  const deadline = new Date(requiredCompletionAt).getTime()

  // Precomputed static domain — a padded band around the data so the price shape is legible.
  // Recharts' function-form domain re-runs during layout and can loop with ResponsiveContainer.
  const ores = data.map((point) => point.ore)
  const yMin = Math.max(0, Math.floor((Math.min(...ores) - 8) / 5) * 5)
  const yMax = Math.ceil((Math.max(...ores) + 5) / 5) * 5

  return (
    <div className="space-y-2">
      <ResponsiveContainer width="100%" height={220} debounce={80}>
        <AreaChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: -8 }}>
          <defs>
            <linearGradient id="charge-price-fill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="var(--color-chart-3)" stopOpacity={0.35} />
              <stop offset="100%" stopColor="var(--color-chart-3)" stopOpacity={0.04} />
            </linearGradient>
          </defs>

          <XAxis
            dataKey="t"
            type="number"
            scale="time"
            domain={['dataMin', 'dataMax']}
            tickFormatter={(t: number) => formatTime(new Date(t).toISOString())}
            tick={{ fontSize: 11, fill: 'var(--color-muted-foreground)' }}
            stroke="var(--color-border)"
            minTickGap={24}
          />
          <YAxis
            width={38}
            domain={[yMin, yMax]}
            allowDecimals={false}
            tickFormatter={(v: number) => `${Math.round(v)}`}
            tick={{ fontSize: 11, fill: 'var(--color-muted-foreground)' }}
            stroke="var(--color-border)"
          />

          <ReferenceArea
            x1={Math.max(earliest, data[0].t)}
            x2={Math.min(deadline, data[data.length - 1].t)}
            fill="var(--color-muted-foreground)"
            fillOpacity={0.06}
          />
          <ReferenceArea
            x1={chargeStart}
            x2={chargeEnd}
            fill="var(--color-chart-2)"
            fillOpacity={0.18}
            stroke="var(--color-chart-2)"
            strokeOpacity={0.5}
          />
          <ReferenceLine
            x={deadline}
            stroke="var(--color-muted-foreground)"
            strokeDasharray="3 3"
            label={{
              value: 'Needed by',
              position: 'insideTopRight',
              style: { fontSize: 10, fill: 'var(--color-muted-foreground)' },
            }}
          />

          <Tooltip
            isAnimationActive={false}
            content={({ active, payload }) => {
              if (!active || !payload?.length) return null
              const point = payload[0].payload as Point
              return (
                <div className="bg-popover text-popover-foreground rounded-md border px-2 py-1 text-xs shadow-sm">
                  <div>{formatTime(new Date(point.t).toISOString())}</div>
                  <div className="font-medium">{formatOrePerKwh(point.ore / 100)}</div>
                </div>
              )
            }}
          />

          <Area
            type="stepAfter"
            dataKey="ore"
            stroke="var(--color-chart-3)"
            strokeWidth={2}
            fill="url(#charge-price-fill)"
            isAnimationActive={false}
          />
        </AreaChart>
      </ResponsiveContainer>

      <div className="text-muted-foreground flex flex-wrap justify-center gap-x-4 gap-y-1 text-xs">
        <span className="flex items-center gap-1.5">
          <span className="bg-chart-2/20 ring-chart-2/50 inline-block size-2.5 rounded-sm ring-1" />
          Charging window
        </span>
        <span className="flex items-center gap-1.5">
          <span className="bg-muted-foreground/10 inline-block size-2.5 rounded-sm" />
          Allowed span
        </span>
        <span>Hourly price in øre/kWh ({prices[0].priceArea})</span>
      </div>
    </div>
  )
}
