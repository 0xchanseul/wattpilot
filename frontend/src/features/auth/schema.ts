import { z } from 'zod'
import { PRICE_AREAS } from '@/lib/price-area'

/** Mirrors the backend's LoginRequest: presence only, no format rules (see the DTO's Javadoc). */
export const loginSchema = z.object({
  email: z.string().min(1, 'Email is required'),
  password: z.string().min(1, 'Password is required'),
  rememberMe: z.boolean(),
})

/** Mirrors the backend's SignUpRequest validation. */
export const signUpSchema = z.object({
  email: z.string().min(1, 'Email is required').email('Enter a valid email address').max(255),
  password: z
    .string()
    .min(8, 'Password must be at least 8 characters')
    .max(72, 'Password must be at most 72 characters'),
  name: z.string().min(1, 'Name is required').max(100),
  defaultPriceArea: z.enum(PRICE_AREAS, { message: 'Select your price area' }),
})

/** Mirrors the backend's UpdateUserRequest validation. The form always submits both fields;
 *  only the ones that actually changed are sent to the API (see ProfilePage). */
export const updateProfileSchema = z.object({
  name: z.string().min(1, 'Name is required').max(100),
  defaultPriceArea: z.enum(PRICE_AREAS, { message: 'Select your price area' }),
})

export type LoginFormValues = z.infer<typeof loginSchema>
export type SignUpFormValues = z.infer<typeof signUpSchema>
export type UpdateProfileFormValues = z.infer<typeof updateProfileSchema>
