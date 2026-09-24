import type { ReactNode } from 'react'
import { ActivityIcon, PercentIcon, TrendingUpIcon, ZapIcon } from 'lucide-react'

import { Card, CardContent } from '@/components/ui/card'
import { formatKwh, formatNok, formatPercent } from '@/lib/format'
import type { SavingsSummary } from '../types'

export function SavingsSummaryCards({ summary }: { summary: SavingsSummary }) {
  return (
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
      <Card className="border-chart-2/40 bg-chart-2/5">
        <CardContent className="space-y-1">
          <p className="text-muted-foreground flex items-center gap-1.5 text-xs">
            <TrendingUpIcon className="size-3.5" /> Total saved
          </p>
          <p className="text-chart-2 text-2xl font-semibold tabular-nums">
            {formatNok(summary.totalSavingsNok)}
          </p>
        </CardContent>
      </Card>
      <Stat
        label="Energy charged"
        value={formatKwh(summary.totalEnergyKwh)}
        icon={<ZapIcon className="size-3.5" />}
      />
      <Stat
        label="Charges"
        value={String(summary.completedSessionCount)}
        icon={<ActivityIcon className="size-3.5" />}
      />
      <Stat
        label="Savings rate"
        value={formatPercent(summary.savingsRatePercent)}
        icon={<PercentIcon className="size-3.5" />}
      />
    </div>
  )
}

function Stat({ label, value, icon }: { label: string; value: string; icon: ReactNode }) {
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
