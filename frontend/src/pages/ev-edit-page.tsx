import { Link, useNavigate, useParams } from 'react-router'
import { ChevronLeftIcon } from 'lucide-react'
import { toast } from 'sonner'

import { ApiErrorAlert } from '@/components/api-error-alert'
import { Skeleton } from '@/components/ui/skeleton'
import { EvForm } from '@/features/ev/components/ev-form'
import type { EvFormValues } from '@/features/ev/schema'
import { useEvQuery, useUpdateEvMutation } from '@/features/ev/queries'
import type { CreateEvInput, Ev, UpdateEvInput } from '@/features/ev/types'

function toFormValues(ev: Ev): EvFormValues {
  return {
    name: ev.name,
    manufacturer: ev.manufacturer,
    model: ev.model,
    batteryCapacityKwh: String(ev.batteryCapacityKwh),
    maxAcChargingPowerKw: String(ev.maxAcChargingPowerKw),
    defaultChargerPowerKw: String(ev.defaultChargerPowerKw),
  }
}

function changedFields(ev: Ev, payload: CreateEvInput): UpdateEvInput {
  const patch: UpdateEvInput = {}
  if (payload.name !== ev.name) patch.name = payload.name
  if (payload.manufacturer !== ev.manufacturer) patch.manufacturer = payload.manufacturer
  if (payload.model !== ev.model) patch.model = payload.model
  if (payload.batteryCapacityKwh !== ev.batteryCapacityKwh)
    patch.batteryCapacityKwh = payload.batteryCapacityKwh
  if (payload.maxAcChargingPowerKw !== ev.maxAcChargingPowerKw)
    patch.maxAcChargingPowerKw = payload.maxAcChargingPowerKw
  if (payload.defaultChargerPowerKw !== ev.defaultChargerPowerKw)
    patch.defaultChargerPowerKw = payload.defaultChargerPowerKw
  return patch
}

export function EvEditPage() {
  const { evId } = useParams()
  const id = Number(evId)
  const navigate = useNavigate()
  const { data: ev, isPending, isError, error } = useEvQuery(id)
  const mutation = useUpdateEvMutation(id)

  const handleSubmit = async (payload: CreateEvInput) => {
    if (!ev) return
    const patch = changedFields(ev, payload)
    if (Object.keys(patch).length === 0) {
      navigate(`/evs/${id}`)
      return
    }
    const updated = await mutation.mutateAsync(patch)
    toast.success(`${updated.name} updated`)
    navigate(`/evs/${id}`)
  }

  return (
    <div className="space-y-6">
      <Link
        to={`/evs/${id}`}
        className="text-muted-foreground hover:text-foreground inline-flex items-center gap-1 text-sm"
      >
        <ChevronLeftIcon className="size-4" /> Back
      </Link>
      <h1 className="text-2xl font-semibold">Edit EV</h1>

      {isPending ? <Skeleton className="h-96 w-full" /> : null}
      {isError ? <ApiErrorAlert error={error} title="Could not load this EV" /> : null}
      {ev ? (
        <EvForm
          defaultValues={toFormValues(ev)}
          submitLabel="Save changes"
          onSubmit={handleSubmit}
          onCancel={() => navigate(`/evs/${id}`)}
        />
      ) : null}
    </div>
  )
}
