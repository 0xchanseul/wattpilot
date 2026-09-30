import { useState } from 'react'
import { Loader2Icon, RefreshCwIcon } from 'lucide-react'
import { toast } from 'sonner'

import { ApiErrorAlert } from '@/components/api-error-alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { errorMessage } from '@/lib/error-message'
import {
  useDisconnectVehicleMutation,
  useVehicleConnectUrlMutation,
  useVehicleConnectionQuery,
  useVehicleTelemetryQuery,
} from '../queries'
import { VehicleChargeStateBadge } from './vehicle-charge-state-badge'

/**
 * Read-only Smartcar vehicle connection (V1.5). Deliberately never reads or writes anything related
 * to Mock Charging execution - it only displays the real vehicle's own reported state, which may
 * legitimately disagree with WattPilot's simulated charging progress (see docs/mvp-scope.md).
 */
export function VehicleTelemetryCard({ evId }: { evId: number }) {
  const connectionQuery = useVehicleConnectionQuery(evId)
  const connected = Boolean(connectionQuery.data)
  const telemetryQuery = useVehicleTelemetryQuery(evId, connected)
  const connectUrl = useVehicleConnectUrlMutation(evId)
  const disconnect = useDisconnectVehicleMutation(evId)
  const [confirmingDisconnect, setConfirmingDisconnect] = useState(false)

  const handleConnect = async () => {
    try {
      const { url } = await connectUrl.mutateAsync()
      window.location.href = url
    } catch (error) {
      toast.error(errorMessage(error))
    }
  }

  const handleDisconnect = async () => {
    try {
      await disconnect.mutateAsync()
      toast.success('Vehicle disconnected')
    } catch (error) {
      toast.error(errorMessage(error))
    } finally {
      setConfirmingDisconnect(false)
    }
  }

  if (connectionQuery.isPending) {
    return <Skeleton className="h-40 w-full" />
  }

  const connection = connectionQuery.data
  const telemetry = telemetryQuery.data

  return (
    <Card>
      <CardHeader>
        <CardTitle>Vehicle connection</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {!connection ? (
          <div className="space-y-3">
            <p className="text-muted-foreground text-sm">
              Connect this EV to Smartcar to see its real battery level and charging status here.
              This is read-only and completely separate from WattPilot&apos;s own simulated (Mock)
              charging - the two numbers may not match.
            </p>
            {connectUrl.isError ? <ApiErrorAlert error={connectUrl.error} /> : null}
            <Button onClick={handleConnect} disabled={connectUrl.isPending}>
              {connectUrl.isPending ? <Loader2Icon className="animate-spin" /> : null}
              Connect vehicle
            </Button>
          </div>
        ) : (
          <div className="space-y-4">
            <div className="flex items-center justify-between gap-3">
              <p className="text-sm font-medium">
                {[connection.make, connection.model, connection.year].filter(Boolean).join(' ') ||
                  'Connected vehicle'}
              </p>
              <Button
                variant="ghost"
                size="icon"
                onClick={() => void telemetryQuery.refetch()}
                disabled={telemetryQuery.isFetching}
                aria-label="Refresh vehicle data"
              >
                <RefreshCwIcon className={telemetryQuery.isFetching ? 'animate-spin' : undefined} />
              </Button>
            </div>

            {telemetryQuery.isPending ? <Skeleton className="h-16 w-full" /> : null}
            {telemetryQuery.isError ? (
              <ApiErrorAlert error={telemetryQuery.error} title="Could not read vehicle data" />
            ) : null}
            {telemetry ? (
              <dl className="grid grid-cols-2 gap-x-8 gap-y-3 sm:grid-cols-4">
                <TelemetryStat
                  label="Battery"
                  value={
                    telemetry.stateOfChargePercent === null
                      ? '—'
                      : `${Math.round(telemetry.stateOfChargePercent)}%`
                  }
                />
                <TelemetryStat
                  label="Range"
                  value={telemetry.rangeKm === null ? '—' : `${Math.round(telemetry.rangeKm)} km`}
                />
                <div className="col-span-2 flex items-end">
                  <VehicleChargeStateBadge isPluggedIn={telemetry.isPluggedIn} isCharging={telemetry.isCharging} />
                </div>
              </dl>
            ) : null}
            <p className="text-muted-foreground text-xs">
              Live data from your vehicle via Smartcar - independent of WattPilot&apos;s simulated
              charging.
            </p>

            {confirmingDisconnect ? (
              <div className="flex items-center gap-2">
                <span className="text-muted-foreground text-sm">Disconnect this vehicle?</span>
                <Button variant="destructive" size="sm" onClick={handleDisconnect} disabled={disconnect.isPending}>
                  Yes, disconnect
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => setConfirmingDisconnect(false)}
                  disabled={disconnect.isPending}
                >
                  Cancel
                </Button>
              </div>
            ) : (
              <Button variant="ghost" size="sm" onClick={() => setConfirmingDisconnect(true)}>
                Disconnect
              </Button>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  )
}

function TelemetryStat({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-muted-foreground text-xs">{label}</dt>
      <dd className="text-lg font-semibold">{value}</dd>
    </div>
  )
}
