import { GaugeIcon, TrendingDownIcon, TrendingUpIcon, ZapOffIcon } from 'lucide-react'

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { useAuth } from '@/features/auth/use-auth'
import { formatOrePerKwh, formatPercent } from '@/lib/format'
import { priceAreaLabel } from '@/lib/price-area'
import { cn } from '@/lib/utils'
import type { DashboardCurrentPrice } from '../types'

export function CurrentPriceCard({ currentPrice }: { currentPrice: DashboardCurrentPrice | null }) {
  const { user } = useAuth()

  return (
    <Card className="h-full">
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <GaugeIcon className="text-muted-foreground size-4" />
          Current price
        </CardTitle>
        {user ? (
          <p className="text-muted-foreground text-xs">{priceAreaLabel(user.defaultPriceArea)}</p>
        ) : null}
      </CardHeader>
      <CardContent>
        {currentPrice ? (
          <PriceReadout currentPrice={currentPrice} />
        ) : (
          <div className="text-muted-foreground flex items-center gap-2 py-4 text-sm">
            <ZapOffIcon className="size-4 shrink-0" />
            Price data isn&apos;t available right now.
          </div>
        )}
      </CardContent>
    </Card>
  )
}

function PriceReadout({ currentPrice }: { currentPrice: DashboardCurrentPrice }) {
  const { priceNokPerKwh, todayAveragePriceNokPerKwh, differencePercent } = currentPrice
  const cheaper = differencePercent != null && differencePercent < 0
  const pricier = differencePercent != null && differencePercent > 0

  return (
    <div className="space-y-3">
      <p className="text-foreground text-2xl font-semibold tabular-nums">
        {formatOrePerKwh(priceNokPerKwh)}
      </p>

      {differencePercent != null && todayAveragePriceNokPerKwh != null ? (
        <div
          className={cn(
            'flex items-center gap-1.5 text-sm font-medium',
            cheaper && 'text-chart-2',
            pricier && 'text-destructive',
          )}
        >
          {cheaper ? <TrendingDownIcon className="size-4 shrink-0" /> : null}
          {pricier ? <TrendingUpIcon className="size-4 shrink-0" /> : null}
          <span>
            {formatPercent(Math.abs(differencePercent))} {cheaper ? 'below' : pricier ? 'above' : 'at'} average
          </span>
        </div>
      ) : null}

      <p className="text-muted-foreground text-xs">
        Today&apos;s average:{' '}
        {todayAveragePriceNokPerKwh != null ? formatOrePerKwh(todayAveragePriceNokPerKwh) : '—'}
      </p>
    </div>
  )
}
