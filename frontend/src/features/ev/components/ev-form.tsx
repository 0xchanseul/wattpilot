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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { applyFieldErrors, errorMessage } from '@/lib/error-message'
import { useVehicleModelsQuery } from '../queries'
import { evFormSchema, toCreateEvInput, type EvFormValues } from '../schema'
import type { CreateEvInput } from '../types'

const MANUAL_ENTRY_VALUE = 'manual'

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
  const { data: vehicleModels } = useVehicleModelsQuery()

  const handleModelPick = (value: string) => {
    if (value === MANUAL_ENTRY_VALUE) {
      return
    }
    const preset = vehicleModels?.find((model) => String(model.id) === value)
    if (!preset) {
      return
    }
    form.setValue('manufacturer', preset.manufacturer, { shouldValidate: true })
    form.setValue('model', preset.model, { shouldValidate: true })
    form.setValue('batteryCapacityKwh', String(preset.batteryCapacityKwh), { shouldValidate: true })
    form.setValue('maxAcChargingPowerKw', String(preset.maxAcChargingPowerKw), { shouldValidate: true })
  }

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

        {vehicleModels && vehicleModels.length > 0 ? (
          <FormItem>
            <FormLabel>Model preset</FormLabel>
            <Select onValueChange={handleModelPick}>
              <FormControl>
                <SelectTrigger className="w-full">
                  <SelectValue placeholder="Select a model to prefill the specs below" />
                </SelectTrigger>
              </FormControl>
              <SelectContent>
                <SelectItem value={MANUAL_ENTRY_VALUE}>Not listed - enter manually</SelectItem>
                {vehicleModels.map((preset) => (
                  <SelectItem key={preset.id} value={String(preset.id)}>
                    {preset.manufacturer} {preset.model}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <FormDescription>
              Optional. Fills in manufacturer, model, battery and max AC charging power below - you can
              still edit any of them afterward.
            </FormDescription>
          </FormItem>
        ) : null}

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
