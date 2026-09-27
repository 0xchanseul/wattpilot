import { useMemo } from 'react'
import { Bar, BarChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { CalendarDaysIcon } from 'lucide-react'

import { formatNok } from '@/lib/format'
import type { PatternGroupBy, SavingsPatternPoint } from '../types'

const WEEKDAY_LABELS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

/** `bucket` is ISO-8601 weekday (1=Monday) for WEEKDAY, or the hour of day (0-23) for HOUR_OF_DAY. */
function bucketLabel(bucket: number, groupBy: PatternGroupBy): string {
  return groupBy === 'HOUR_OF_DAY' ? `${String(bucket).padStart(2, '0')}:00` : (WEEKDAY_LABELS[bucket - 1] ?? String(bucket))
}

/**
 * Realized savings re-bucketed by a recurring weekday or hour of day instead of by calendar date —
 * "when do I actually save the most," decoupled from any particular week or month.
 */
export function SavingsPatternChart({
  points,
  groupBy,
}: {
  points: SavingsPatternPoint[]
  groupBy: PatternGroupBy
}) {
  const hasSavings = useMemo(() => points.some((point) => point.savingsNok > 0), [points])

  if (!hasSavings) {
    return (
      <div className="text-muted-foreground flex flex-col items-center gap-2 py-14 text-center text-sm">
        <CalendarDaysIcon className="size-6" />
        No savings yet to break down.
      </div>
    )
  }

  return (
    <ResponsiveContainer width="100%" height={240} debounce={80}>
      <BarChart data={points} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
        <XAxis
          dataKey="bucket"
          tickFormatter={(value: number) => bucketLabel(value, groupBy)}
          tick={{ fontSize: 11, fill: 'var(--color-muted-foreground)' }}
          stroke="var(--color-border)"
          interval={groupBy === 'HOUR_OF_DAY' ? 1 : 0}
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
            const point = payload[0].payload as SavingsPatternPoint
            return (
              <div className="bg-popover text-popover-foreground rounded-md border px-2.5 py-1.5 text-xs shadow-sm">
                <div className="text-muted-foreground">{bucketLabel(point.bucket, groupBy)}</div>
                <div className="text-chart-2 font-medium">{formatNok(point.savingsNok)}</div>
                <div className="text-muted-foreground">
                  {point.sessionCount} session{point.sessionCount === 1 ? '' : 's'}
                </div>
              </div>
            )
          }}
        />

        <Bar dataKey="savingsNok" fill="var(--color-chart-2)" radius={[4, 4, 0, 0]} isAnimationActive={false} />
      </BarChart>
    </ResponsiveContainer>
  )
}
