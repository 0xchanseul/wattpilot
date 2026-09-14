import { useMemo } from 'react'
import { Area, AreaChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { TrendingUpIcon } from 'lucide-react'

import { formatDate, formatNok, formatShortDate } from '@/lib/format'
import type { DashboardSavingsTrendPoint } from '../types'

/**
 * Last-30-days realized savings as a filled area chart — an Area (not Line) so the "accumulated
 * value" read matches WattPilot's savings-focused framing, using the same chart-2/CSS-variable
 * theming as {@code ChargePriceChart}.
 */
export function SavingsTrendChart({ trend }: { trend: DashboardSavingsTrendPoint[] }) {
  const hasSavings = useMemo(() => trend.some((point) => point.savingsNok > 0), [trend])

  if (!hasSavings) {
    return (
      <div className="text-muted-foreground flex flex-col items-center gap-2 py-14 text-center text-sm">
        <TrendingUpIcon className="size-6" />
        No savings yet in the last 30 days. They&apos;ll show up here as charges complete.
      </div>
    )
  }

  const values = trend.map((point) => point.savingsNok)
  const yMax = Math.max(5, Math.ceil((Math.max(...values) * 1.15) / 5) * 5)

  return (
    <ResponsiveContainer width="100%" height={240} debounce={80}>
      <AreaChart data={trend} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
        <defs>
          <linearGradient id="savings-trend-fill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--color-chart-2)" stopOpacity={0.35} />
            <stop offset="100%" stopColor="var(--color-chart-2)" stopOpacity={0.03} />
          </linearGradient>
        </defs>

        <XAxis
          dataKey="date"
          tickFormatter={(value: string) => formatShortDate(value)}
          tick={{ fontSize: 11, fill: 'var(--color-muted-foreground)' }}
          stroke="var(--color-border)"
          minTickGap={28}
        />
        <YAxis
          width={44}
          domain={[0, yMax]}
          tickFormatter={(v: number) => `${Math.round(v)}`}
          tick={{ fontSize: 11, fill: 'var(--color-muted-foreground)' }}
          stroke="var(--color-border)"
        />

        <Tooltip
          isAnimationActive={false}
          content={({ active, payload }) => {
            if (!active || !payload?.length) return null
            const point = payload[0].payload as DashboardSavingsTrendPoint
            return (
              <div className="bg-popover text-popover-foreground rounded-md border px-2.5 py-1.5 text-xs shadow-sm">
                <div className="text-muted-foreground">{formatDate(point.date)}</div>
                <div className="text-chart-2 font-medium">{formatNok(point.savingsNok)}</div>
              </div>
            )
          }}
        />

        <Area
          type="monotone"
          dataKey="savingsNok"
          stroke="var(--color-chart-2)"
          strokeWidth={2}
          fill="url(#savings-trend-fill)"
          isAnimationActive={false}
        />
      </AreaChart>
    </ResponsiveContainer>
  )
}
