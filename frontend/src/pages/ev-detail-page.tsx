import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ChevronLeftIcon, PencilIcon } from 'lucide-react'
import { toast } from 'sonner'

import { ApiErrorAlert } from '@/components/api-error-alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { EvStatusBadge } from '@/features/ev/components/ev-status-badge'
import {
  useDeactivateEvMutation,
  useEvQuery,
  useUpdateEvMutation,
} from '@/features/ev/queries'
import { errorMessage } from '@/lib/error-message'
import { formatDateTime, formatKw, formatKwh } from '@/lib/format'

export function EvDetailPage() {
  const { evId } = useParams()
  const id = Number(evId)
  const navigate = useNavigate()
  const { data: ev, isPending, isError, error } = useEvQuery(id)

  const deactivate = useDeactivateEvMutation(id)
  const reactivate = useUpdateEvMutation(id)
  const [confirmingDeactivate, setConfirmingDeactivate] = useState(false)

  const handleDeactivate = async () => {
    try {
      await deactivate.mutateAsync()
      toast.success('EV deactivated')
      navigate('/evs')
    } catch (deactivateError) {
      toast.error(errorMessage(deactivateError))
      setConfirmingDeactivate(false)
    }
  }

  const handleReactivate = async () => {
    try {
      await reactivate.mutateAsync({ status: 'ACTIVE' })
      toast.success('EV reactivated')
    } catch (reactivateError) {
      toast.error(errorMessage(reactivateError))
    }
  }

  return (
    <div className="space-y-6">
      <Link to="/evs" className="text-muted-foreground hover:text-foreground inline-flex items-center gap-1 text-sm">
        <ChevronLeftIcon className="size-4" /> My EVs
      </Link>

      {isPending ? <Skeleton className="h-72 w-full" /> : null}
      {isError ? <ApiErrorAlert error={error} title="Could not load this EV" /> : null}

      {ev ? (
        <>
          <div className="flex items-start justify-between gap-3">
            <div>
              <h1 className="text-2xl font-semibold">{ev.name}</h1>
              <p className="text-muted-foreground">
                {ev.manufacturer} {ev.model}
              </p>
            </div>
            <EvStatusBadge status={ev.status} />
          </div>

          <Card>
            <CardHeader>
              <CardTitle>Specifications</CardTitle>
            </CardHeader>
            <CardContent>
              <dl className="grid grid-cols-1 gap-x-8 gap-y-3 sm:grid-cols-2">
                <SpecRow label="Battery capacity" value={formatKwh(ev.batteryCapacityKwh)} />
                <SpecRow label="Max AC charging power" value={formatKw(ev.maxAcChargingPowerKw)} />
                <SpecRow label="Default charger power" value={formatKw(ev.defaultChargerPowerKw)} />
                <SpecRow label="Registered" value={formatDateTime(ev.createdAt)} />
                <SpecRow label="Last updated" value={formatDateTime(ev.updatedAt)} />
              </dl>
            </CardContent>
          </Card>

          {ev.status === 'ACTIVE' ? (
            <div className="flex flex-wrap items-center gap-3">
              <Button asChild variant="outline">
                <Link to={`/evs/${ev.id}/edit`}>
                  <PencilIcon /> Edit
                </Link>
              </Button>

              {confirmingDeactivate ? (
                <div className="flex items-center gap-2">
                  <span className="text-muted-foreground text-sm">Deactivate this EV?</span>
                  <Button
                    variant="destructive"
                    size="sm"
                    onClick={handleDeactivate}
                    disabled={deactivate.isPending}
                  >
                    Yes, deactivate
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => setConfirmingDeactivate(false)}
                    disabled={deactivate.isPending}
                  >
                    Cancel
                  </Button>
                </div>
              ) : (
                <Button variant="ghost" onClick={() => setConfirmingDeactivate(true)}>
                  Deactivate
                </Button>
              )}
            </div>
          ) : (
            <div className="flex flex-wrap items-center gap-3">
              <p className="text-muted-foreground text-sm">
                This EV is deactivated and hidden from your list.
              </p>
              <Button variant="outline" onClick={handleReactivate} disabled={reactivate.isPending}>
                Reactivate
              </Button>
            </div>
          )}
        </>
      ) : null}
    </div>
  )
}

function SpecRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-4 border-b pb-2 last:border-0 sm:border-0 sm:pb-0">
      <dt className="text-muted-foreground text-sm">{label}</dt>
      <dd className="text-sm font-medium">{value}</dd>
    </div>
  )
}
