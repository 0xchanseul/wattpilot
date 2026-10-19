import { useState } from 'react'
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

import { Button } from '@/components/ui/button'
import { formatDate, formatShortDate } from '@/lib/format'
import type { DailyPoint } from '../types'

type Series = 'visitors' | 'signups' | 'schedules'

const SERIES: { key: Series; label: string; color: string }[] = [
  { key: 'visitors', label: 'Visitors', color: 'var(--color-chart-1)' },
  { key: 'signups', label: 'Sign-ups', color: 'var(--color-chart-2)' },
  { key: 'schedules', label: 'Schedules', color: 'var(--color-chart-4)' },
]

export function DailyTrendChart({ points }: { points: DailyPoint[] }) {
  const [active, setActive] = useState<Series>('visitors')
  const series = SERIES.find((item) => item.key === active) ?? SERIES[0]

  return (
    <div className="space-y-3">
      <div className="flex gap-1">
        {SERIES.map((item) => (
          <Button
            key={item.key}
            type="button"
            size="sm"
            variant={active === item.key ? 'default' : 'outline'}
            onClick={() => setActive(item.key)}
          >
            {item.label}
          </Button>
        ))}
      </div>

      <ResponsiveContainer width="100%" height={240} debounce={80}>
        <LineChart data={points} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
          <CartesianGrid stroke="var(--color-border)" strokeDasharray="3 3" vertical={false} />
          <XAxis
            dataKey="date"
            tickFormatter={(value: string) => formatShortDate(value)}
            tick={{ fontSize: 11, fill: 'var(--color-muted-foreground)' }}
            stroke="var(--color-border)"
            minTickGap={28}
          />
          <YAxis
            width={44}
            allowDecimals={false}
            domain={[0, (max: number) => Math.max(3, max)]}
            tick={{ fontSize: 11, fill: 'var(--color-muted-foreground)' }}
            stroke="var(--color-border)"
          />
          <Tooltip
            isAnimationActive={false}
            content={({ active: isActive, payload }) => {
              if (!isActive || !payload?.length) return null
              const point = payload[0].payload as DailyPoint
              return (
                <div className="bg-popover text-popover-foreground rounded-md border px-2.5 py-1.5 text-xs shadow-sm">
                  <div className="text-muted-foreground">{formatDate(point.date)}</div>
                  <div className="font-medium">
                    {series.label}: {point[series.key]}
                  </div>
                </div>
              )
            }}
          />
          <Line
            type="monotone"
            dataKey={series.key}
            stroke={series.color}
            strokeWidth={2}
            dot={points.length <= 31}
            isAnimationActive={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}
