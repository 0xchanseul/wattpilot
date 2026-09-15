import { useMemo } from 'react'
import { Area, AreaChart, ResponsiveContainer } from 'recharts'
import { PiggyBankIcon, TrendingUpIcon } from 'lucide-react'

import { formatNok, formatPercent } from '@/lib/format'
import type { DashboardSavingsTrendPoint, DashboardSummary } from '../types'

/**
 * The dashboard's second hero tile — WattPilot's core value (realized savings) on the same
 * aurora-tinted surface as the logo, backed by the real savings trend as a sparkline rather than
 * a decorative motif.
 */
export function SavingsHeroCard({
  summary,
  trend,
  savingsPercent,
}: {
  summary: DashboardSummary
  trend: DashboardSavingsTrendPoint[]
  savingsPercent: number
}) {
  const sparkline = useMemo(() => trend.slice(-14), [trend])
  const hasSavings = summary.totalSavingsNok > 0

  return (
    <div className="border-primary/15 relative flex h-full min-h-72 flex-col overflow-hidden rounded-xl border bg-gradient-to-br from-[#e3f7f0] to-[#dff3ee] p-6">
      <div
        aria-hidden
        className="bg-aurora-gradient absolute -top-16 -right-16 size-40 rounded-full opacity-20 blur-2xl"
      />

      <div className="relative flex items-center justify-between gap-2">
        <div className="text-primary flex items-center gap-1.5 text-sm font-medium">
          <PiggyBankIcon className="size-4" />
          Total savings
        </div>
        {hasSavings && savingsPercent > 0 ? (
          <span className="bg-primary/10 text-primary flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium">
            <TrendingUpIcon className="size-3" />
            {formatPercent(savingsPercent)}
          </span>
        ) : null}
      </div>

      <p className="relative mt-3 text-4xl font-semibold tabular-nums">
        {formatNok(summary.totalSavingsNok)}
      </p>
      <p className="text-muted-foreground relative mt-1 text-sm">
        Since your first optimized charge
      </p>

      <div className="relative mt-auto -mb-2 h-16">
        {sparkline.length > 1 ? (
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={sparkline} margin={{ top: 4, right: 0, bottom: 0, left: 0 }}>
              <defs>
                <linearGradient id="savings-hero-fill" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="var(--aurora-green)" stopOpacity={0.45} />
                  <stop offset="100%" stopColor="var(--aurora-green)" stopOpacity={0.02} />
                </linearGradient>
              </defs>
              <Area
                type="monotone"
                dataKey="savingsNok"
                stroke="var(--aurora-green)"
                strokeWidth={2}
                fill="url(#savings-hero-fill)"
                isAnimationActive={false}
              />
            </AreaChart>
          </ResponsiveContainer>
        ) : null}
      </div>
    </div>
  )
}
