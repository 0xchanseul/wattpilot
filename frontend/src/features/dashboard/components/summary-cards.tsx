import type { ReactNode } from 'react'
import { BatteryChargingIcon, CoinsIcon, TrendingUpIcon, ZapIcon } from 'lucide-react'

import { Card, CardContent } from '@/components/ui/card'
import { formatKwh, formatNok, formatNokPerKwh } from '@/lib/format'
import type { DashboardSummary } from '../types'

/**
 * The four headline KPIs. Total Savings is WattPilot's core value, so it gets its own emphasized
 * card (bigger number, tinted background) — matching the "Total saved" treatment on the Charging
 * History page — while the other three stay visually secondary.
 */
export function SummaryCards({ summary }: { summary: DashboardSummary }) {
  return (
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
      <Card className="border-chart-2/40 bg-chart-2/5 sm:col-span-2 lg:col-span-1">
        <CardContent className="space-y-1">
          <p className="text-muted-foreground flex items-center gap-1.5 text-xs">
            <TrendingUpIcon className="size-3.5" /> Total savings
          </p>
          <p className="text-chart-2 text-2xl font-semibold tabular-nums">
            {formatNok(summary.totalSavingsNok)}
          </p>
          <p className="text-muted-foreground text-xs">Since your first optimized charge</p>
        </CardContent>
      </Card>
      <StatCard
        label="Energy charged"
        value={formatKwh(summary.totalEnergyKwh)}
        icon={<ZapIcon className="size-3.5" />}
      />
      <StatCard
        label="Charging sessions"
        value={String(summary.totalSessions)}
        icon={<BatteryChargingIcon className="size-3.5" />}
      />
      <StatCard
        label="Average cost"
        value={formatNokPerKwh(summary.averageCostPerKwh)}
        icon={<CoinsIcon className="size-3.5" />}
      />
    </div>
  )
}

function StatCard({ label, value, icon }: { label: string; value: string; icon: ReactNode }) {
  return (
    <Card>
      <CardContent className="space-y-1">
        <p className="text-muted-foreground flex items-center gap-1.5 text-xs">
          {icon} {label}
        </p>
        <p className="text-xl font-semibold tabular-nums">{value}</p>
      </CardContent>
    </Card>
  )
}
