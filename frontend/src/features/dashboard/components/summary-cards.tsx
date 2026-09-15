import type { ReactNode } from 'react'
import { BatteryChargingIcon, CoinsIcon, GaugeIcon, TrendingDownIcon, TrendingUpIcon, ZapIcon, ZapOffIcon } from 'lucide-react'

import { Card, CardContent } from '@/components/ui/card'
import { useAuth } from '@/features/auth/use-auth'
import { formatKwh, formatNokPerKwh, formatOrePerKwh, formatPercent } from '@/lib/format'
import { priceAreaLabel } from '@/lib/price-area'
import { cn } from '@/lib/utils'
import type { DashboardCurrentPrice, DashboardSummary } from '../types'

/**
 * The secondary KPI row. Total savings gets its own hero tile ({@link SavingsHeroCard}), so this
 * row stays uniformly-weighted supporting detail: current price, energy charged, sessions, and
 * average cost.
 */
export function SummaryCards({
  summary,
  currentPrice,
}: {
  summary: DashboardSummary
  currentPrice: DashboardCurrentPrice | null
}) {
  return (
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
      <CurrentPriceTile currentPrice={currentPrice} />
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
    <Card className="gap-2 py-5">
      <CardContent className="space-y-1.5">
        <p className="text-muted-foreground flex items-center gap-1.5 text-xs">
          <span className="bg-accent text-primary flex size-6 items-center justify-center rounded-full">
            {icon}
          </span>
          {label}
        </p>
        <p className="text-xl font-semibold tabular-nums">{value}</p>
      </CardContent>
    </Card>
  )
}

function CurrentPriceTile({ currentPrice }: { currentPrice: DashboardCurrentPrice | null }) {
  const { user } = useAuth()

  return (
    <Card className="gap-2 py-5">
      <CardContent className="space-y-1.5">
        <p className="text-muted-foreground flex items-center gap-1.5 text-xs">
          <span className="bg-accent text-primary flex size-6 items-center justify-center rounded-full">
            <GaugeIcon className="size-3.5" />
          </span>
          Current price
          {user ? <span className="text-muted-foreground/70">· {priceAreaLabel(user.defaultPriceArea)}</span> : null}
        </p>

        {currentPrice ? (
          <div className="flex items-baseline gap-2">
            <p className="text-xl font-semibold tabular-nums">{formatOrePerKwh(currentPrice.priceNokPerKwh)}</p>
            {currentPrice.differencePercent != null ? (
              <PriceDelta value={currentPrice.differencePercent} />
            ) : null}
          </div>
        ) : (
          <div className="text-muted-foreground flex items-center gap-1.5 py-0.5 text-sm">
            <ZapOffIcon className="size-3.5 shrink-0" />
            Unavailable
          </div>
        )}
      </CardContent>
    </Card>
  )
}

function PriceDelta({ value }: { value: number }) {
  const cheaper = value < 0
  const pricier = value > 0
  return (
    <span
      className={cn(
        'flex items-center gap-0.5 text-xs font-medium',
        cheaper && 'text-chart-2',
        pricier && 'text-destructive',
      )}
    >
      {cheaper ? <TrendingDownIcon className="size-3" /> : null}
      {pricier ? <TrendingUpIcon className="size-3" /> : null}
      {formatPercent(Math.abs(value))}
    </span>
  )
}
