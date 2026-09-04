import { z } from 'zod'

import { PRICE_AREAS } from '@/lib/price-area'
import type { CreateChargingPlanPreviewRequest } from './types'

/**
 * Charging-conditions form. Numeric fields are kept as strings (the inputs bind to strings);
 * {@link toPreviewRequest} parses them on submit. Mirrors the Bean Validation on
 * `CreateChargingPlanPreviewRequest` plus the service-level rule `target > current`.
 */

const BATTERY_MAX_DECIMALS = 2

function batteryPercent(options: { allowZero: boolean }) {
  return z
    .string()
    .min(1, 'Required')
    .refine((value) => !Number.isNaN(Number(value)), 'Enter a number')
    .refine((value) => {
      const n = Number(value)
      return options.allowZero ? n >= 0 : n > 0
    }, options.allowZero ? 'Must be between 0 and 100' : 'Must be greater than 0')
    .refine((value) => Number(value) <= 100, 'Must be at most 100')
    .refine((value) => {
      const decimals = value.split('.')[1]?.length ?? 0
      return decimals <= BATTERY_MAX_DECIMALS
    }, `At most ${BATTERY_MAX_DECIMALS} decimal places`)
}

export const chargingConditionsSchema = z
  .object({
    evId: z.string().min(1, 'Select an EV'),
    currentBatteryPercent: batteryPercent({ allowZero: true }),
    targetBatteryPercent: batteryPercent({ allowZero: false }),
    /** `datetime-local` value, e.g. "2026-09-04T07:00" (browser-local wall time). */
    requiredCompletionAt: z
      .string()
      .min(1, 'Choose when charging must be finished')
      .refine((value) => !Number.isNaN(new Date(value).getTime()), 'Enter a valid date and time')
      .refine((value) => new Date(value).getTime() > Date.now(), 'Must be in the future'),
    priceArea: z.enum(PRICE_AREAS, { message: 'Select a price area' }),
  })
  .refine(
    (values) => Number(values.targetBatteryPercent) > Number(values.currentBatteryPercent),
    { path: ['targetBatteryPercent'], message: 'Target must be higher than the current level' },
  )

export type ChargingConditionsValues = z.infer<typeof chargingConditionsSchema>

/** Converts a browser-local `datetime-local` value into a full ISO string with offset. */
export function datetimeLocalToIso(value: string): string {
  return new Date(value).toISOString()
}

export function toPreviewRequest(
  values: ChargingConditionsValues,
): CreateChargingPlanPreviewRequest {
  return {
    evId: Number(values.evId),
    currentBatteryPercent: Number(values.currentBatteryPercent),
    targetBatteryPercent: Number(values.targetBatteryPercent),
    requiredCompletionAt: datetimeLocalToIso(values.requiredCompletionAt),
    priceArea: values.priceArea,
  }
}

const TWO_DIGITS = (n: number) => String(n).padStart(2, '0')

/** Formats a Date as a `datetime-local` input value in browser-local time. */
export function toDatetimeLocalValue(date: Date): string {
  return (
    `${date.getFullYear()}-${TWO_DIGITS(date.getMonth() + 1)}-${TWO_DIGITS(date.getDate())}` +
    `T${TWO_DIGITS(date.getHours())}:${TWO_DIGITS(date.getMinutes())}`
  )
}

/** Sensible default deadline: 07:00 tomorrow, local time. */
export function defaultDeadlineValue(now: Date = new Date()): string {
  const deadline = new Date(now)
  deadline.setDate(deadline.getDate() + 1)
  deadline.setHours(7, 0, 0, 0)
  return toDatetimeLocalValue(deadline)
}
