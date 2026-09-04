import { useMemo } from 'react'

import { cn } from '@/lib/utils'
import { formatOrePerKwh, formatTime } from '@/lib/format'
import type { ElectricityPrice } from '@/features/electricity/types'

interface PriceTimelineProps {
  prices: ElectricityPrice[]
  /** ISO start/end of the selected continuous charging window. */
  windowStartAt: string
  windowEndAt: string
}

/**
 * A lightweight hourly-price bar chart: each hour is a bar whose height tracks its price, and the
 * hours covered by the selected charging window are highlighted. Deliberately CSS-only — no chart
 * library — to keep the MVP simple.
 */
export function PriceTimeline({ prices, windowStartAt, windowEndAt }: PriceTimelineProps) {
  const windowStart = new Date(windowStartAt).getTime()
  const windowEnd = new Date(windowEndAt).getTime()

  const bars = useMemo(() => {
    const maxPrice = Math.max(...prices.map((price) => price.pricePerKwh), 0)
    return prices.map((price) => {
      const start = new Date(price.startsAt).getTime()
      const end = new Date(price.endsAt).getTime()
      const inWindow = start < windowEnd && end > windowStart
      const heightPercent = maxPrice > 0 ? Math.max(6, (price.pricePerKwh / maxPrice) * 100) : 6
      return { price, inWindow, heightPercent }
    })
  }, [prices, windowStart, windowEnd])

  if (prices.length === 0) {
    return null
  }

  const first = prices[0]
  const last = prices[prices.length - 1]

  return (
    <div className="space-y-2">
      <div className="flex h-32 items-end gap-0.5 overflow-x-auto rounded-md border bg-muted/30 p-2">
        {bars.map(({ price, inWindow, heightPercent }) => (
          <div
            key={price.id}
            title={`${formatTime(price.startsAt)} · ${formatOrePerKwh(price.pricePerKwh)}`}
            className="flex h-full min-w-[6px] flex-1 items-end"
          >
            <div
              className={cn(
                'w-full rounded-sm transition-colors',
                inWindow ? 'bg-chart-2' : 'bg-muted-foreground/25',
              )}
              style={{ height: `${heightPercent}%` }}
            />
          </div>
        ))}
      </div>
      <div className="text-muted-foreground flex justify-between text-xs">
        <span>{formatTime(first.startsAt)}</span>
        <span className="flex items-center gap-3">
          <span className="flex items-center gap-1">
            <span className="bg-chart-2 inline-block size-2 rounded-sm" /> Charging window
          </span>
          <span className="flex items-center gap-1">
            <span className="bg-muted-foreground/25 inline-block size-2 rounded-sm" /> Other hours
          </span>
        </span>
        <span>{formatTime(last.endsAt)}</span>
      </div>
    </div>
  )
}
