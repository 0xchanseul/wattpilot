import { useMemo } from 'react'
import { Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { PercentIcon } from 'lucide-react'

import { formatMonth, formatPercent } from '@/lib/format'
import type { DailySavings } from '../types'

/**
 * Savings rate (realized savings as a percentage of baseline) per calendar month — separates "is the
 * optimizer working well" from "how much did I charge," which the absolute-NOK chart above conflates:
 * a light-charging month can still show a strong rate, and vice versa.
 */
export function MonthlySavingsRateChart({ points }: { points: DailySavings[] }) {
  const hasData = useMemo(() => points.some((point) => point.sessionCount > 0), [points])

  if (!hasData) {
    return (
      <div className="text-muted-foreground flex flex-col items-center gap-2 py-14 text-center text-sm">
        <PercentIcon className="size-6" />
        No savings yet in this range.
      </div>
    )
  }

  return (
    <ResponsiveContainer width="100%" height={200} debounce={80}>
      <LineChart data={points} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
        <XAxis
          dataKey="date"
          tickFormatter={(value: string) => formatMonth(value)}
          tick={{ fontSize: 11, fill: 'var(--color-muted-foreground)' }}
          stroke="var(--color-border)"
        />
        <YAxis
          width={44}
          tickFormatter={(v: number) => `${Math.round(v)}%`}
          tick={{ fontSize: 11, fill: 'var(--color-muted-foreground)' }}
          stroke="var(--color-border)"
        />

        <Tooltip
          isAnimationActive={false}
          content={({ active, payload }) => {
            if (!active || !payload?.length) return null
            const point = payload[0].payload as DailySavings
            return (
              <div className="bg-popover text-popover-foreground rounded-md border px-2.5 py-1.5 text-xs shadow-sm">
                <div className="text-muted-foreground">{formatMonth(point.date)}</div>
                <div className="text-chart-2 font-medium">{formatPercent(point.savingsRatePercent)}</div>
              </div>
            )
          }}
        />

        <Line
          type="monotone"
          dataKey="savingsRatePercent"
          stroke="var(--color-chart-2)"
          strokeWidth={2}
          dot={{ r: 3 }}
          isAnimationActive={false}
        />
      </LineChart>
    </ResponsiveContainer>
  )
}
