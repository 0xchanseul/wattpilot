import { useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { AlertCircleIcon, ChevronLeftIcon } from 'lucide-react'
import { toast } from 'sonner'
import type { DefaultValues } from 'react-hook-form'

import { ApiErrorAlert } from '@/components/api-error-alert'
import { ApiError } from '@/lib/api-error'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useAuth } from '@/features/auth/use-auth'
import { useEvsQuery } from '@/features/ev/queries'
import { useElectricityPricesQuery } from '@/features/electricity/queries'
import { CandidateSelector } from '@/features/charging/components/candidate-selector'
import { ChargingConditionsForm } from '@/features/charging/components/charging-conditions-form'
import { ChargingSummaryCard } from '@/features/charging/components/charging-summary-card'
import {
  useChargingPlanPreviewMutation,
  useCreateChargingScheduleMutation,
} from '@/features/charging/queries'
import { chargingErrorCopy } from '@/features/charging/error-copy'
import {
  defaultDeadlineValue,
  toPreviewRequest,
  type ChargingConditionsValues,
} from '@/features/charging/schema'
import type {
  ChargingPlanPreviewResponse,
  CreateChargingPlanPreviewRequest,
  CreateChargingScheduleRequest,
} from '@/features/charging/types'

interface ReviewState {
  conditions: ChargingConditionsValues
  request: CreateChargingPlanPreviewRequest
  response: ChargingPlanPreviewResponse
}

function floorToHourIso(date: Date): string {
  const floored = new Date(date)
  floored.setMinutes(0, 0, 0)
  return floored.toISOString()
}

export function ChargingPlanNewPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const [searchParams] = useSearchParams()

  const evsQuery = useEvsQuery({ status: 'ACTIVE', size: 100 })
  const previewMutation = useChargingPlanPreviewMutation()
  const createMutation = useCreateChargingScheduleMutation()

  const [step, setStep] = useState<'conditions' | 'review'>('conditions')
  const [review, setReview] = useState<ReviewState | null>(null)
  const [selectedRank, setSelectedRank] = useState(1)

  const evs = useMemo(() => evsQuery.data?.content ?? [], [evsQuery.data])

  const preselectedEvId = useMemo(() => {
    const raw = searchParams.get('evId')
    if (raw && evs.some((ev) => String(ev.id) === raw)) {
      return raw
    }
    return evs.length === 1 ? String(evs[0].id) : ''
  }, [searchParams, evs])

  const formDefaults = useMemo<DefaultValues<ChargingConditionsValues>>(
    () => ({
      evId: preselectedEvId,
      currentBatteryPercent: '',
      targetBatteryPercent: '80',
      requiredCompletionAt: defaultDeadlineValue(),
      priceArea: user?.defaultPriceArea,
    }),
    [preselectedEvId, user],
  )

  const selectedCandidate =
    review?.response.candidates.find((candidate) => candidate.rank === selectedRank) ??
    review?.response.candidates[0]

  const pricesQuery = useElectricityPricesQuery(
    step === 'review' && review
      ? {
          priceArea: review.request.priceArea,
          from: floorToHourIso(new Date()),
          to: review.request.requiredCompletionAt,
        }
      : null,
  )

  const handlePreview = async (values: ChargingConditionsValues) => {
    const request = toPreviewRequest(values)
    const response = await previewMutation.mutateAsync(request)
    setReview({ conditions: values, request, response })
    setSelectedRank(response.candidates[0]?.rank ?? 1)
    createMutation.reset()
    setStep('review')
    window.scrollTo({ top: 0 })
  }

  const handleRecalculate = async () => {
    if (!review) return
    try {
      const response = await previewMutation.mutateAsync(review.request)
      setReview({ ...review, response })
      setSelectedRank(response.candidates[0]?.rank ?? 1)
      createMutation.reset()
    } catch {
      // Surfaced via previewMutation.error below.
    }
  }

  const handleConfirm = async () => {
    if (!review || !selectedCandidate) return
    if (createMutation.isPending || createMutation.isSuccess) return

    const body: CreateChargingScheduleRequest = {
      evId: review.request.evId,
      currentBatteryPercent: review.request.currentBatteryPercent,
      targetBatteryPercent: review.request.targetBatteryPercent,
      requiredCompletionAt: review.request.requiredCompletionAt,
      priceArea: review.request.priceArea,
      selectedStartAt: selectedCandidate.recommendedStartAt,
      selectedEndAt: selectedCandidate.recommendedEndAt,
    }

    try {
      const schedule = await createMutation.mutateAsync(body)
      toast.success('Charging scheduled')
      navigate(`/charging/schedules/${schedule.id}`, { replace: true })
    } catch {
      // Surfaced via createMutation.error below.
    }
  }

  const goBackToConditions = () => {
    createMutation.reset()
    setStep('conditions')
    window.scrollTo({ top: 0 })
  }

  const evName = review
    ? evs.find((ev) => ev.id === review.request.evId)?.name
    : undefined

  const confirmError = createMutation.error
  const confirmErrorCopy = confirmError ? chargingErrorCopy(confirmError) : null
  const recalculateError =
    step === 'review' && previewMutation.error ? chargingErrorCopy(previewMutation.error) : null

  return (
    <div className="space-y-6">
      <Link
        to="/charging/schedules"
        className="text-muted-foreground hover:text-foreground inline-flex items-center gap-1 text-sm"
      >
        <ChevronLeftIcon className="size-4" /> Charging schedules
      </Link>

      <div>
        <h1 className="text-2xl font-semibold">Plan charging</h1>
        <p className="text-muted-foreground text-sm">
          {step === 'conditions'
            ? 'Enter your charging conditions to find the cheapest window.'
            : 'Review the recommended window, then confirm the schedule.'}
        </p>
      </div>

      <ol className="text-muted-foreground flex flex-wrap gap-x-2 gap-y-1 text-sm">
        <li className={step === 'conditions' ? 'text-foreground font-medium' : undefined}>
          1. Conditions
        </li>
        <li aria-hidden>·</li>
        <li className={step === 'review' ? 'text-foreground font-medium' : undefined}>
          2. Review &amp; confirm
        </li>
      </ol>

      {evsQuery.isPending ? <Skeleton className="h-96 w-full" /> : null}
      {evsQuery.isError ? (
        <ApiErrorAlert error={evsQuery.error} title="Could not load your EVs" />
      ) : null}

      {evsQuery.data ? (
        <>
          <div className={step === 'conditions' ? undefined : 'hidden'}>
            <ChargingConditionsForm
              evs={evs}
              defaultValues={formDefaults}
              onSubmit={handlePreview}
            />
          </div>

          {step === 'review' && review ? (
            <div className="space-y-6">
              <Button variant="ghost" size="sm" onClick={goBackToConditions}>
                <ChevronLeftIcon /> Change conditions
              </Button>

              {review.response.candidates.length === 0 ? (
                <Alert>
                  <AlertCircleIcon />
                  <AlertTitle>No windows returned</AlertTitle>
                  <AlertDescription>Adjust your conditions and try again.</AlertDescription>
                </Alert>
              ) : (
                <>
                  <CandidateSelector
                    candidates={review.response.candidates}
                    selectedRank={selectedRank}
                    onSelect={setSelectedRank}
                  />

                  {selectedCandidate ? (
                    <ChargingSummaryCard
                      title="Schedule preview"
                      evName={evName}
                      priceArea={review.request.priceArea}
                      startAt={selectedCandidate.recommendedStartAt}
                      endAt={selectedCandidate.recommendedEndAt}
                      durationMinutes={review.response.estimatedDurationMinutes}
                      batteryFromPercent={review.request.currentBatteryPercent}
                      batteryToPercent={review.request.targetBatteryPercent}
                      calculatedEnergyKwh={review.response.calculatedEnergyKwh}
                      expectedEnergyKwh={selectedCandidate.expectedEnergyKwh}
                      estimatedCostNok={selectedCandidate.estimatedCostNok}
                      baselineCostNok={selectedCandidate.baselineCostNok}
                      expectedSavingsNok={selectedCandidate.expectedSavingsNok}
                      slots={selectedCandidate.slots}
                      prices={pricesQuery.data?.prices}
                    />
                  ) : null}

                  {recalculateError ? (
                    <Alert variant="destructive">
                      <AlertCircleIcon />
                      <AlertTitle>{recalculateError.title}</AlertTitle>
                      <AlertDescription>{recalculateError.description}</AlertDescription>
                    </Alert>
                  ) : null}

                  {confirmErrorCopy ? (
                    <Alert variant="destructive">
                      <AlertCircleIcon />
                      <AlertTitle>{confirmErrorCopy.title}</AlertTitle>
                      <AlertDescription className="space-y-2">
                        <p>{confirmErrorCopy.description}</p>
                        <div className="flex gap-2">
                          {confirmError instanceof ApiError &&
                          confirmError.code === 'CHARGING_CANDIDATE_UNAVAILABLE' ? (
                            <Button
                              size="sm"
                              variant="outline"
                              onClick={handleRecalculate}
                              disabled={previewMutation.isPending}
                            >
                              {previewMutation.isPending ? 'Recalculating…' : 'Recalculate'}
                            </Button>
                          ) : null}
                          {confirmErrorCopy.adjustConditions ? (
                            <Button size="sm" variant="outline" onClick={goBackToConditions}>
                              Change conditions
                            </Button>
                          ) : null}
                        </div>
                      </AlertDescription>
                    </Alert>
                  ) : null}

                  <div className="flex flex-wrap items-center gap-3">
                    <Button
                      onClick={handleConfirm}
                      disabled={
                        !selectedCandidate ||
                        createMutation.isPending ||
                        createMutation.isSuccess
                      }
                    >
                      {createMutation.isPending
                        ? 'Scheduling…'
                        : 'Confirm & schedule charging'}
                    </Button>
                    <Button variant="outline" onClick={goBackToConditions}>
                      Cancel
                    </Button>
                  </div>
                </>
              )}
            </div>
          ) : null}
        </>
      ) : null}
    </div>
  )
}
