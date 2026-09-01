import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router'
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
import { signUpSchema, type SignUpFormValues } from '@/features/auth/schema'
import { applyFieldErrors, errorMessage } from '@/lib/error-message'
import { PRICE_AREAS, priceAreaLabel } from '@/lib/price-area'

const SIGN_UP_FIELDS = ['email', 'password', 'name', 'defaultPriceArea'] as const

export function SignUpPage() {
  const { signUp } = useAuth()
  const navigate = useNavigate()

  const form = useForm<SignUpFormValues>({
    resolver: zodResolver(signUpSchema),
    defaultValues: { email: '', password: '', name: '', defaultPriceArea: undefined },
  })

  const onSubmit = form.handleSubmit(async (values) => {
    try {
      await signUp(values)
      navigate('/evs', { replace: true })
    } catch (error) {
      const handled = applyFieldErrors(
        error,
        (field, fieldError) => form.setError(field as keyof SignUpFormValues, fieldError),
        SIGN_UP_FIELDS,
      )
      if (!handled) {
        form.setError('root', { message: errorMessage(error) })
      }
    }
  })

  const rootError = form.formState.errors.root?.message

  return (
    <div className="flex min-h-svh items-center justify-center px-4 py-10">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <div className="flex items-center gap-2">
            <img src="/favicon.svg" alt="" className="size-7" />
            <CardTitle className="text-lg">Create your account</CardTitle>
          </div>
          <CardDescription>It takes a minute. No payment details.</CardDescription>
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
                      <Input autoComplete="name" placeholder="Iris" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="email"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Email</FormLabel>
                    <FormControl>
                      <Input type="email" autoComplete="email" placeholder="iris@example.com" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="password"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Password</FormLabel>
                    <FormControl>
                      <Input type="password" autoComplete="new-password" {...field} />
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

              <Button type="submit" className="w-full" disabled={form.formState.isSubmitting}>
                Create account
              </Button>
            </form>
          </Form>

          <p className="text-muted-foreground mt-4 text-center text-sm">
            Already have an account?{' '}
            <Link to="/login" className="text-foreground font-medium underline underline-offset-4">
              Sign in
            </Link>
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
