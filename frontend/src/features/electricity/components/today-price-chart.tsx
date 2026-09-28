import { useMemo } from 'react'
import { Area, AreaChart, ReferenceArea, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { ZapIcon } from 'lucide-react'

import type { ElectricityPrice } from '@/features/electricity/types'
import { formatOrePerKwh, formatTime } from '@/lib/format'

interface TodayPriceChartProps {
  /** Already scoped to today's Europe/Oslo calendar day. */
  prices: ElectricityPrice[]
  /** How many of today's cheapest hours to highlight. */
  cheapestCount?: number
}

interface Point {
  t: number
  ore: number
}

const DEFAULT_CHEAPEST_COUNT = 3

/**
 * Today's hourly electricity price with the cheapest hours highlighted, so the Dashboard answers
 * "when is it cheap today" at a glance, without opening the charging flow. Same step-area styling as
 * {@code ChargePriceChart} (History), which shades a specific charge window instead of the cheapest
 * hours of the whole day.
 */
export function TodayPriceChart({ prices, cheapestCount = DEFAULT_CHEAPEST_COUNT }: TodayPriceChartProps) {
  const sorted = useMemo(
    () => [...prices].sort((a, b) => a.startsAt.localeCompare(b.startsAt)),
    [prices],
  )

  const data = useMemo<Point[]>(
    () =>
      sorted.flatMap((price) => [
        { t: new Date(price.startsAt).getTime(), ore: price.pricePerKwh * 100 },
        { t: new Date(price.endsAt).getTime(), ore: price.pricePerKwh * 100 },
      ]),
    [sorted],
  )

  const cheapestHours = useMemo(
    () => [...sorted].sort((a, b) => a.pricePerKwh - b.pricePerKwh).slice(0, cheapestCount),
    [sorted, cheapestCount],
  )

  if (data.length === 0) {
    return (
      <div className="text-muted-foreground flex flex-col items-center gap-2 py-14 text-center text-sm">
        <ZapIcon className="size-6" />
        No prices for today yet.
      </div>
    )
  }

  // Precomputed static domain, same reasoning as ChargePriceChart: a function-form domain re-runs
  // during layout and can loop with ResponsiveContainer.
  const ores = data.map((point) => point.ore)
  const yMin = Math.max(0, Math.floor((Math.min(...ores) - 8) / 5) * 5)
  const yMax = Math.ceil((Math.max(...ores) + 5) / 5) * 5

  const now = Date.now()
  const showNowLine = now >= data[0].t && now <= data[data.length - 1].t

  return (
    <div className="space-y-2">
      <ResponsiveContainer width="100%" height={200} debounce={80}>
        <AreaChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: -8 }}>
          <defs>
            <linearGradient id="today-price-fill" x1="0" y1="0" x2="0" y2="1">
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

          {cheapestHours.map((price) => (
            <ReferenceArea
              key={price.id}
              x1={new Date(price.startsAt).getTime()}
              x2={new Date(price.endsAt).getTime()}
              fill="var(--color-chart-2)"
              fillOpacity={0.18}
              stroke="var(--color-chart-2)"
              strokeOpacity={0.5}
            />
          ))}

          {showNowLine ? (
            <ReferenceLine
              x={now}
              stroke="var(--color-muted-foreground)"
              strokeDasharray="3 3"
              label={{
                value: 'Now',
                position: 'insideTopRight',
                style: { fontSize: 10, fill: 'var(--color-muted-foreground)' },
              }}
            />
          ) : null}

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
            fill="url(#today-price-fill)"
            isAnimationActive={false}
          />
        </AreaChart>
      </ResponsiveContainer>

      <div className="text-muted-foreground flex flex-wrap justify-center gap-x-4 gap-y-1 text-xs">
        <span className="flex items-center gap-1.5">
          <span className="bg-chart-2/20 ring-chart-2/50 inline-block size-2.5 rounded-sm ring-1" />
          {cheapestHours.length} cheapest hour{cheapestHours.length === 1 ? '' : 's'}
        </span>
        <span>Hourly price in øre/kWh ({sorted[0].priceArea})</span>
      </div>
    </div>
  )
}
