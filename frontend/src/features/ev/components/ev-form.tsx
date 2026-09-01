import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { AlertCircleIcon } from 'lucide-react'

import { Alert, AlertDescription } from '@/components/ui/alert'
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
import { applyFieldErrors, errorMessage } from '@/lib/error-message'
import { evFormSchema, toCreateEvInput, type EvFormValues } from '../schema'
import type { CreateEvInput } from '../types'

const EV_FIELD_NAMES = [
  'name',
  'manufacturer',
  'model',
  'batteryCapacityKwh',
  'maxAcChargingPowerKw',
  'defaultChargerPowerKw',
] as const

const EMPTY: EvFormValues = {
  name: '',
  manufacturer: '',
  model: '',
  batteryCapacityKwh: '',
  maxAcChargingPowerKw: '',
  defaultChargerPowerKw: '',
}

export interface EvFormProps {
  defaultValues?: Partial<EvFormValues>
  submitLabel: string
  onSubmit: (payload: CreateEvInput) => Promise<void>
  onCancel?: () => void
}

export function EvForm({ defaultValues, submitLabel, onSubmit, onCancel }: EvFormProps) {
  const form = useForm<EvFormValues>({
    resolver: zodResolver(evFormSchema),
    defaultValues: { ...EMPTY, ...defaultValues },
  })

  const handleSubmit = form.handleSubmit(async (values) => {
    try {
      await onSubmit(toCreateEvInput(values))
    } catch (error) {
      const handled = applyFieldErrors(
        error,
        (field, fieldError) => form.setError(field as keyof EvFormValues, fieldError),
        EV_FIELD_NAMES,
      )
      if (!handled) {
        form.setError('root', { message: errorMessage(error) })
      }
    }
  })

  const rootError = form.formState.errors.root?.message

  return (
    <Form {...form}>
      <form onSubmit={handleSubmit} className="space-y-6" noValidate>
        {rootError ? (
          <Alert variant="destructive">
            <AlertCircleIcon />
            <AlertDescription>{rootError}</AlertDescription>
          </Alert>
        ) : null}

        <FormField
          control={form.control}
          name="name"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Name</FormLabel>
              <FormControl>
                <Input placeholder="My i4" {...field} />
              </FormControl>
              <FormDescription>A label to tell this EV apart from your others.</FormDescription>
              <FormMessage />
            </FormItem>
          )}
        />

        <div className="grid gap-6 sm:grid-cols-2">
          <FormField
            control={form.control}
            name="manufacturer"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Manufacturer</FormLabel>
                <FormControl>
                  <Input placeholder="BMW" {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="model"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Model</FormLabel>
                <FormControl>
                  <Input placeholder="i4 eDrive40" {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>

        <FormField
          control={form.control}
          name="batteryCapacityKwh"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Battery capacity (kWh)</FormLabel>
              <FormControl>
                <Input
                  type="number"
                  inputMode="decimal"
                  step="0.01"
                  min="0"
                  placeholder="81.1"
                  {...field}
                />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />

        <div className="grid gap-6 sm:grid-cols-2">
          <FormField
            control={form.control}
            name="maxAcChargingPowerKw"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Max AC charging power (kW)</FormLabel>
                <FormControl>
                  <Input
                    type="number"
                    inputMode="decimal"
                    step="0.01"
                    min="0"
                    max="22"
                    placeholder="11"
                    {...field}
                  />
                </FormControl>
                <FormDescription>The most the car itself can take on AC.</FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="defaultChargerPowerKw"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Default charger power (kW)</FormLabel>
                <FormControl>
                  <Input
                    type="number"
                    inputMode="decimal"
                    step="0.01"
                    min="0"
                    max="22"
                    placeholder="7.4"
                    {...field}
                  />
                </FormControl>
                <FormDescription>The output of the charger you usually plug into.</FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>

        <div className="flex gap-3">
          <Button type="submit" disabled={form.formState.isSubmitting}>
            {submitLabel}
          </Button>
          {onCancel ? (
            <Button type="button" variant="outline" onClick={onCancel}>
              Cancel
            </Button>
          ) : null}
        </div>
      </form>
    </Form>
  )
}
