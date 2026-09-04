import { useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm, type DefaultValues } from 'react-hook-form'
import { Link } from 'react-router'
import { AlertCircleIcon } from 'lucide-react'

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import type { Ev } from '@/features/ev/types'
import { applyFieldErrors } from '@/lib/error-message'
import { PRICE_AREAS, priceAreaLabel } from '@/lib/price-area'
import {
  chargingConditionsSchema,
  toDatetimeLocalValue,
  type ChargingConditionsValues,
} from '../schema'
import { chargingErrorCopy } from '../error-copy'

const FIELD_NAMES = [
  'evId',
  'currentBatteryPercent',
  'targetBatteryPercent',
  'requiredCompletionAt',
  'priceArea',
] as const

export interface ChargingConditionsFormProps {
  evs: Ev[]
  defaultValues: DefaultValues<ChargingConditionsValues>
  /** Runs the preview. Rejects with an ApiError the form turns into inline / banner messages. */
  onSubmit: (values: ChargingConditionsValues) => Promise<void>
  submitLabel?: string
}

export function ChargingConditionsForm({
  evs,
  defaultValues,
  onSubmit,
  submitLabel = 'Calculate charging options',
}: ChargingConditionsFormProps) {
  const [bannerError, setBannerError] = useState<unknown>(null)

  const form = useForm<ChargingConditionsValues>({
    resolver: zodResolver(chargingConditionsSchema),
    defaultValues,
  })

  const handleSubmit = form.handleSubmit(async (values) => {
    setBannerError(null)
    try {
      await onSubmit(values)
    } catch (error) {
      const handled = applyFieldErrors(
        error,
        (field, fieldError) => form.setError(field as keyof ChargingConditionsValues, fieldError),
        FIELD_NAMES,
      )
      if (!handled) {
        setBannerError(error)
      }
    }
  })

  const minDeadline = toDatetimeLocalValue(new Date())

  if (evs.length === 0) {
    return (
      <Alert>
        <AlertCircleIcon />
        <AlertTitle>Register an EV first</AlertTitle>
        <AlertDescription>
          You need an active EV before you can plan charging.{' '}
          <Link to="/evs/new" className="text-foreground font-medium underline underline-offset-4">
            Register an EV
          </Link>
          .
        </AlertDescription>
      </Alert>
    )
  }

  const banner = bannerError ? chargingErrorCopy(bannerError) : null

  return (
    <Form {...form}>
      <form onSubmit={handleSubmit} className="space-y-6" noValidate>
        {banner ? (
          <Alert variant="destructive">
            <AlertCircleIcon />
            <AlertTitle>{banner.title}</AlertTitle>
            <AlertDescription>{banner.description}</AlertDescription>
          </Alert>
        ) : null}

        <FormField
          control={form.control}
          name="evId"
          render={({ field }) => (
            <FormItem>
              <FormLabel>EV</FormLabel>
              <Select value={field.value ?? ''} onValueChange={field.onChange}>
                <FormControl>
                  <SelectTrigger className="w-full">
                    <SelectValue placeholder="Choose an EV" />
                  </SelectTrigger>
                </FormControl>
                <SelectContent>
                  {evs.map((ev) => (
                    <SelectItem key={ev.id} value={String(ev.id)}>
                      {ev.name} — {ev.manufacturer} {ev.model}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <FormMessage />
            </FormItem>
          )}
        />

        <div className="grid gap-6 sm:grid-cols-2">
          <FormField
            control={form.control}
            name="currentBatteryPercent"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Current battery level (%)</FormLabel>
                <FormControl>
                  <Input
                    type="number"
                    inputMode="decimal"
                    step="1"
                    min="0"
                    max="100"
                    placeholder="30"
                    {...field}
                  />
                </FormControl>
                <FormDescription>Where the battery is right now.</FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="targetBatteryPercent"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Target battery level (%)</FormLabel>
                <FormControl>
                  <Input
                    type="number"
                    inputMode="decimal"
                    step="1"
                    min="0"
                    max="100"
                    placeholder="80"
                    {...field}
                  />
                </FormControl>
                <FormDescription>The level you want when charging is done.</FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>

        <FormField
          control={form.control}
          name="requiredCompletionAt"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Finish charging by</FormLabel>
              <FormControl>
                <Input type="datetime-local" min={minDeadline} {...field} />
              </FormControl>
              <FormDescription>
                Charging starts from now and must complete before this time (shown in your local
                time).
              </FormDescription>
              <FormMessage />
            </FormItem>
          )}
        />

        <FormField
          control={form.control}
          name="priceArea"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Price area</FormLabel>
              <Select value={field.value ?? ''} onValueChange={field.onChange}>
                <FormControl>
                  <SelectTrigger className="w-full">
                    <SelectValue placeholder="Select a price area" />
                  </SelectTrigger>
                </FormControl>
                <SelectContent>
                  {PRICE_AREAS.map((area) => (
                    <SelectItem key={area} value={area}>
                      {priceAreaLabel(area)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <FormDescription>The Norwegian bidding zone your charger is in.</FormDescription>
              <FormMessage />
            </FormItem>
          )}
        />

        <Button type="submit" disabled={form.formState.isSubmitting}>
          {form.formState.isSubmitting ? 'Calculating…' : submitLabel}
        </Button>
      </form>
    </Form>
  )
}
