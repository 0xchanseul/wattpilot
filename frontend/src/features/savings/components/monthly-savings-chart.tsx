import { useMemo } from 'react'
import { Bar, BarChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { BarChart3Icon } from 'lucide-react'

import { formatMonth, formatNok } from '@/lib/format'
import type { DailySavings } from '../types'

/**
 * Realized savings per calendar month, aggregated server-side (`GET /savings/daily?granularity=MONTHLY`)
 * rather than rolled up in the browser — see docs/tech-stack-architecture.md "Savings".
 */
export function MonthlySavingsChart({ points }: { points: DailySavings[] }) {
  const hasSavings = useMemo(() => points.some((point) => point.savingsNok > 0), [points])

  if (!hasSavings) {
    return (
      <div className="text-muted-foreground flex flex-col items-center gap-2 py-14 text-center text-sm">
        <BarChart3Icon className="size-6" />
        No savings yet in this range.
      </div>
    )
  }

  return (
    <ResponsiveContainer width="100%" height={240} debounce={80}>
      <BarChart data={points} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
        <XAxis
          dataKey="date"
          tickFormatter={(value: string) => formatMonth(value)}
          tick={{ fontSize: 11, fill: 'var(--color-muted-foreground)' }}
          stroke="var(--color-border)"
        />
        <YAxis
          width={44}
          tickFormatter={(v: number) => `${Math.round(v)}`}
          tick={{ fontSize: 11, fill: 'var(--color-muted-foreground)' }}
          stroke="var(--color-border)"
        />

        <Tooltip
          isAnimationActive={false}
          cursor={{ fill: 'var(--color-muted)', opacity: 0.4 }}
          content={({ active, payload }) => {
            if (!active || !payload?.length) return null
            const point = payload[0].payload as DailySavings
            return (
              <div className="bg-popover text-popover-foreground rounded-md border px-2.5 py-1.5 text-xs shadow-sm">
                <div className="text-muted-foreground">{formatMonth(point.date)}</div>
                <div className="text-chart-2 font-medium">{formatNok(point.savingsNok)}</div>
              </div>
            )
          }}
        />

        <Bar dataKey="savingsNok" fill="var(--color-chart-2)" radius={[4, 4, 0, 0]} isAnimationActive={false} />
      </BarChart>
    </ResponsiveContainer>
  )
}
