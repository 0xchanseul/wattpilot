import { ScaleIcon, TrendingDownIcon } from 'lucide-react'

import { formatNok, formatPercent } from '@/lib/format'
import type { DashboardCostComparison } from '../types'

/**
 * A comparison-bar visualization (not a chart library) — two proportional bars are the fastest way
 * to feel "with WattPilot costs less" at a glance, backed by a prominent saved-amount callout.
 */
export function CostComparisonCard({ comparison }: { comparison: DashboardCostComparison }) {
  const { baselineCostNok, optimizedCostNok, savingsNok, savingsPercent } = comparison

  if (baselineCostNok <= 0) {
    return (
      <div className="text-muted-foreground flex flex-col items-center gap-2 py-10 text-center text-sm">
        <ScaleIcon className="size-6" />
        No completed charges in the last 30 days yet to compare.
      </div>
    )
  }

  const optimizedWidth = Math.min(100, (optimizedCostNok / baselineCostNok) * 100)

  return (
    <div className="space-y-5">
      <div className="space-y-3">
        <ComparisonBar label="Without optimization" value={baselineCostNok} widthPercent={100} tone="muted" />
        <ComparisonBar
          label="With WattPilot"
          value={optimizedCostNok}
          widthPercent={optimizedWidth}
          tone="savings"
        />
      </div>

      <div className="bg-chart-2/5 border-chart-2/30 flex items-center gap-3 rounded-lg border px-4 py-3">
        <TrendingDownIcon className="text-chart-2 size-5 shrink-0" />
        <div>
          <p className="text-chart-2 text-lg font-semibold tabular-nums">
            {formatNok(savingsNok)} saved
          </p>
          <p className="text-muted-foreground text-xs">
            {formatPercent(savingsPercent)} lower than charging at typical prices
          </p>
        </div>
      </div>
    </div>
  )
}

function ComparisonBar({
  label,
  value,
  widthPercent,
  tone,
}: {
  label: string
  value: number
  widthPercent: number
  tone: 'muted' | 'savings'
}) {
  return (
    <div className="space-y-1">
      <div className="flex items-baseline justify-between text-sm">
        <span className="text-muted-foreground">{label}</span>
        <span className="font-medium tabular-nums">{formatNok(value)}</span>
      </div>
      <div className="bg-muted h-2.5 w-full overflow-hidden rounded-full">
        <div
          className={tone === 'savings' ? 'bg-chart-2 h-full rounded-full' : 'bg-muted-foreground/40 h-full rounded-full'}
          style={{ width: `${widthPercent}%` }}
        />
      </div>
    </div>
  )
}
