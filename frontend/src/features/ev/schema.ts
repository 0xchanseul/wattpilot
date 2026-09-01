import { z } from 'zod'
import type { CreateEvInput } from './types'

const text = z.string().trim().min(1, 'Required').max(100, 'At most 100 characters')

const POWER_LIMIT_KW = 22

/**
 * Required numeric text field. Kept as a string end to end (the inputs bind to strings and an
 * existing EV seeds them as strings); {@link toCreateEvInput} does the parse on submit.
 */
function numericText(max?: number, maxMessage?: string) {
  return z
    .string()
    .min(1, 'Required')
    .refine((value) => !Number.isNaN(Number(value)), 'Enter a number')
    .refine((value) => Number(value) > 0, 'Must be greater than 0')
    .refine(
      (value) => max === undefined || Number(value) <= max,
      maxMessage ?? `Must be at most ${max}`,
    )
}

/** Mirrors the backend's CreateEvRequest validation (charging power capped at 22 kW). */
export const evFormSchema = z.object({
  name: text,
  manufacturer: text,
  model: text,
  batteryCapacityKwh: numericText(),
  maxAcChargingPowerKw: numericText(POWER_LIMIT_KW, 'Home AC charging is at most 22 kW'),
  defaultChargerPowerKw: numericText(POWER_LIMIT_KW, 'Home AC charging is at most 22 kW'),
})

export type EvFormValues = z.infer<typeof evFormSchema>

export function toCreateEvInput(values: EvFormValues): CreateEvInput {
  return {
    name: values.name,
    manufacturer: values.manufacturer,
    model: values.model,
    batteryCapacityKwh: Number(values.batteryCapacityKwh),
    maxAcChargingPowerKw: Number(values.maxAcChargingPowerKw),
    defaultChargerPowerKw: Number(values.defaultChargerPowerKw),
  }
}
