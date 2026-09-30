import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { AlertCircleIcon } from 'lucide-react'
import { toast } from 'sonner'

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { FullPageSpinner } from '@/components/full-page-spinner'
import { getVehicleConnectCandidates, linkVehicleConnection } from '@/features/ev/api'
import type { VehicleCandidate } from '@/features/ev/types'
import { errorMessage } from '@/lib/error-message'

type Step =
  | { kind: 'loading' }
  | { kind: 'linking' }
  | { kind: 'error'; message: string }
  | { kind: 'choose'; evId: number; smartcarUserId: string; state: string; vehicles: VehicleCandidate[] }

/**
 * Where Smartcar Connect redirects the browser back to (see docs/deployment.md for the registered
 * redirect URI). Reached by a full page reload from an external domain, not a client-side
 * navigation, so there is no stale query-cache concern here - the SPA and its QueryClient are
 * starting fresh.
 */
export function VehicleConnectionCallbackPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const [step, setStep] = useState<Step>({ kind: 'loading' })

  useEffect(() => {
    let cancelled = false

    async function run() {
      const smartcarError = searchParams.get('error')
      if (smartcarError) {
        const description = searchParams.get('error_description')
        setStep({
          kind: 'error',
          message:
            smartcarError === 'access_denied'
              ? 'You declined to connect a vehicle.'
              : (description ?? 'Could not connect a vehicle.'),
        })
        return
      }

      const state = searchParams.get('state')
      const smartcarUserId = searchParams.get('user_id')
      if (!state || !smartcarUserId) {
        setStep({ kind: 'error', message: 'This connection link is invalid or has expired.' })
        return
      }

      try {
        const result = await getVehicleConnectCandidates({ state, smartcarUserId })
        if (cancelled) return
        if (result.vehicles.length === 0) {
          setStep({ kind: 'error', message: 'No vehicles were found in this Smartcar account.' })
        } else if (result.vehicles.length === 1) {
          await link(result.evId, smartcarUserId, state, result.vehicles[0].smartcarVehicleId)
        } else {
          setStep({ kind: 'choose', evId: result.evId, smartcarUserId, state, vehicles: result.vehicles })
        }
      } catch (error) {
        if (!cancelled) setStep({ kind: 'error', message: errorMessage(error) })
      }
    }

    async function link(evId: number, smartcarUserId: string, state: string, smartcarVehicleId: string) {
      setStep({ kind: 'linking' })
      try {
        await linkVehicleConnection(evId, { state, smartcarUserId, smartcarVehicleId })
        if (cancelled) return
        toast.success('Vehicle connected')
        navigate(`/evs/${evId}`, { replace: true })
      } catch (error) {
        if (!cancelled) setStep({ kind: 'error', message: errorMessage(error) })
      }
    }

    void run()
    return () => {
      cancelled = true
    }
    // Runs once on mount against the redirect's query params; searchParams never changes afterward.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handlePick = async (vehicle: VehicleCandidate) => {
    if (step.kind !== 'choose') return
    setStep({ kind: 'linking' })
    try {
      await linkVehicleConnection(step.evId, {
        state: step.state,
        smartcarUserId: step.smartcarUserId,
        smartcarVehicleId: vehicle.smartcarVehicleId,
      })
      toast.success('Vehicle connected')
      navigate(`/evs/${step.evId}`, { replace: true })
    } catch (error) {
      setStep({ kind: 'error', message: errorMessage(error) })
    }
  }

  if (step.kind === 'loading' || step.kind === 'linking') {
    return <FullPageSpinner />
  }

  if (step.kind === 'error') {
    return (
      <div className="mx-auto max-w-md space-y-4 p-6">
        <Alert variant="destructive">
          <AlertCircleIcon />
          <AlertTitle>Could not connect a vehicle</AlertTitle>
          <AlertDescription>{step.message}</AlertDescription>
        </Alert>
        <Button asChild>
          <Link to="/evs">Back to my EVs</Link>
        </Button>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-md space-y-4 p-6">
      <Card>
        <CardHeader>
          <CardTitle>Choose a vehicle</CardTitle>
        </CardHeader>
        <CardContent className="space-y-2">
          {step.vehicles.map((vehicle) => (
            <Button
              key={vehicle.smartcarVehicleId}
              variant="outline"
              className="w-full justify-start"
              onClick={() => void handlePick(vehicle)}
            >
              {[vehicle.make, vehicle.model, vehicle.year].filter(Boolean).join(' ') || vehicle.smartcarVehicleId}
            </Button>
          ))}
        </CardContent>
      </Card>
    </div>
  )
}
