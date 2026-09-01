import { useState } from 'react'
import { Link } from 'react-router'
import { CarIcon, EyeIcon, EyeOffIcon, PlusIcon } from 'lucide-react'

import { ApiErrorAlert } from '@/components/api-error-alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { EvStatusBadge } from '@/features/ev/components/ev-status-badge'
import { useEvsQuery } from '@/features/ev/queries'
import type { Ev } from '@/features/ev/types'
import { cn } from '@/lib/utils'
import { formatKw, formatKwh } from '@/lib/format'

// V1 users have a handful of EVs; ask for the whole list and skip a pager (contract still paginates).
const PAGE_SIZE = 100

export function EvListPage() {
  const { data, isPending, isError, error, refetch } = useEvsQuery({ size: PAGE_SIZE })
  const { data: deactivatedData } = useEvsQuery({ status: 'INACTIVE', size: PAGE_SIZE })
  const [showDeactivated, setShowDeactivated] = useState(false)

  const deactivatedEvs = deactivatedData?.content ?? []
  const hasDeactivated = deactivatedEvs.length > 0

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="text-2xl font-semibold">My EVs</h1>
        <div className="flex items-center gap-2">
          {hasDeactivated ? (
            <Button
              variant="ghost"
              size="sm"
              aria-pressed={showDeactivated}
              onClick={() => setShowDeactivated((value) => !value)}
            >
              {showDeactivated ? <EyeOffIcon /> : <EyeIcon />}
              {showDeactivated ? 'Hide' : 'Show'} deactivated ({deactivatedEvs.length})
            </Button>
          ) : null}
          <Button asChild>
            <Link to="/evs/new">
              <PlusIcon /> Register EV
            </Link>
          </Button>
        </div>
      </div>

      {isPending ? <EvListSkeleton /> : null}

      {isError ? (
        <div className="space-y-3">
          <ApiErrorAlert error={error} title="Could not load your EVs" />
          <Button variant="outline" onClick={() => refetch()}>
            Try again
          </Button>
        </div>
      ) : null}

      {data && data.content.length === 0 && !hasDeactivated ? <EmptyState /> : null}

      {data && data.content.length === 0 && hasDeactivated ? (
        <p className="text-muted-foreground text-sm">
          All of your EVs are deactivated. Use &ldquo;Show deactivated&rdquo; to see them.
        </p>
      ) : null}

      {data && data.content.length > 0 ? (
        <ul className="grid gap-4 sm:grid-cols-2">
          {data.content.map((ev) => (
            <li key={ev.id}>
              <EvCard ev={ev} />
            </li>
          ))}
        </ul>
      ) : null}

      {showDeactivated && hasDeactivated ? (
        <div className="space-y-3">
          <h2 className="text-muted-foreground text-sm font-medium">Deactivated</h2>
          <ul className="grid gap-4 sm:grid-cols-2">
            {deactivatedEvs.map((ev) => (
              <li key={ev.id}>
                <EvCard ev={ev} deactivated />
              </li>
            ))}
          </ul>
        </div>
      ) : null}
    </div>
  )
}

function EvCard({ ev, deactivated = false }: { ev: Ev; deactivated?: boolean }) {
  return (
    <Link to={`/evs/${ev.id}`} className="block h-full">
      <Card
        className={cn(
          'hover:border-ring h-full gap-3 transition-colors',
          deactivated && 'opacity-60 hover:opacity-100',
        )}
      >
        <CardHeader>
          <div className="flex items-start justify-between gap-2">
            <CardTitle>{ev.name}</CardTitle>
            <EvStatusBadge status={ev.status} />
          </div>
          <p className="text-muted-foreground text-sm">
            {ev.manufacturer} {ev.model}
          </p>
        </CardHeader>
        <CardContent className="text-muted-foreground text-sm">
          <dl className="grid grid-cols-2 gap-x-4 gap-y-1">
            <dt>Battery</dt>
            <dd className="text-foreground text-right">{formatKwh(ev.batteryCapacityKwh)}</dd>
            <dt>Max AC</dt>
            <dd className="text-foreground text-right">{formatKw(ev.maxAcChargingPowerKw)}</dd>
            <dt>Charger</dt>
            <dd className="text-foreground text-right">{formatKw(ev.defaultChargerPowerKw)}</dd>
          </dl>
        </CardContent>
      </Card>
    </Link>
  )
}

function EmptyState() {
  return (
    <Card>
      <CardContent className="flex flex-col items-center gap-3 py-12 text-center">
        <div className="bg-secondary flex size-12 items-center justify-center rounded-full">
          <CarIcon className="text-muted-foreground size-6" />
        </div>
        <div>
          <p className="font-medium">No EVs yet</p>
          <p className="text-muted-foreground text-sm">
            Register your car to start planning cheaper charging.
          </p>
        </div>
        <Button asChild>
          <Link to="/evs/new">
            <PlusIcon /> Register your first EV
          </Link>
        </Button>
      </CardContent>
    </Card>
  )
}

function EvListSkeleton() {
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      {Array.from({ length: 4 }).map((_, index) => (
        <Skeleton key={index} className="h-44 w-full" />
      ))}
    </div>
  )
}
