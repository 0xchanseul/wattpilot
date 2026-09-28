import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { AlertCircleIcon } from 'lucide-react'

import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Form,
  FormControl,
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
import { useAuth } from '@/features/auth/use-auth'
import { updateProfileSchema, type UpdateProfileFormValues } from '@/features/auth/schema'
import type { UpdateProfileInput } from '@/features/auth/types'
import { applyFieldErrors, errorMessage } from '@/lib/error-message'
import { PRICE_AREAS, priceAreaLabel } from '@/lib/price-area'

const PROFILE_FIELDS = ['name', 'defaultPriceArea'] as const

function changedFields(
  current: UpdateProfileFormValues,
  submitted: UpdateProfileFormValues,
): UpdateProfileInput {
  const patch: UpdateProfileInput = {}
  if (submitted.name !== current.name) patch.name = submitted.name
  if (submitted.defaultPriceArea !== current.defaultPriceArea) {
    patch.defaultPriceArea = submitted.defaultPriceArea
  }
  return patch
}

export function ProfilePage() {
  const { user, updateProfile } = useAuth()

  const form = useForm<UpdateProfileFormValues>({
    resolver: zodResolver(updateProfileSchema),
    defaultValues: { name: user?.name ?? '', defaultPriceArea: user?.defaultPriceArea },
  })

  const rootError = form.formState.errors.root?.message

  if (!user) {
    return null
  }

  const onSubmit = form.handleSubmit(async (values) => {
    const patch = changedFields({ name: user.name, defaultPriceArea: user.defaultPriceArea }, values)
    if (Object.keys(patch).length === 0) {
      toast.info('Nothing to save')
      return
    }
    try {
      await updateProfile(patch)
      toast.success('Profile updated')
    } catch (error) {
      const handled = applyFieldErrors(
        error,
        (field, fieldError) => form.setError(field as keyof UpdateProfileFormValues, fieldError),
        PROFILE_FIELDS,
      )
      if (!handled) {
        form.setError('root', { message: errorMessage(error) })
      }
    }
  })

  return (
    <div className="mx-auto max-w-lg space-y-6">
      <h1 className="text-2xl font-semibold">Profile</h1>

      <Card>
        <CardHeader>
          <CardTitle className="text-xl">Account details</CardTitle>
          <CardDescription>{user.email}</CardDescription>
        </CardHeader>
        <CardContent>
          <Form {...form}>
            <form onSubmit={onSubmit} className="space-y-4" noValidate>
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
                      <Input autoComplete="name" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="defaultPriceArea"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Default price area</FormLabel>
                    <Select value={field.value ?? ''} onValueChange={field.onChange}>
                      <FormControl>
                        <SelectTrigger className="w-full">
                          <SelectValue placeholder="Select your region" />
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
                    <FormMessage />
                  </FormItem>
                )}
              />

              <Button type="submit" disabled={form.formState.isSubmitting}>
                Save changes
              </Button>
            </form>
          </Form>
        </CardContent>
      </Card>
    </div>
  )
}
