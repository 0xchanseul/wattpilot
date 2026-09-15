import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Link, useLocation, useNavigate } from 'react-router'
import { AlertCircleIcon } from 'lucide-react'

import { Alert, AlertDescription } from '@/components/ui/alert'
import { AuthShell } from '@/components/layout/auth-shell'
import { Button } from '@/components/ui/button'
import { CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { useAuth } from '@/features/auth/use-auth'
import { loginSchema, type LoginFormValues } from '@/features/auth/schema'
import { errorMessage } from '@/lib/error-message'

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? '/dashboard'

  const form = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '', rememberMe: false },
  })

  const onSubmit = form.handleSubmit(async (values) => {
    try {
      await login(values)
      navigate(from, { replace: true })
    } catch (error) {
      form.setError('root', { message: errorMessage(error) })
    }
  })

  const rootError = form.formState.errors.root?.message

  return (
    <AuthShell tagline="Charging, timed to the cheapest hour of the day.">
      <div className="mb-6 flex items-center gap-2 lg:hidden">
        <img src="/favicon.svg" alt="" className="size-7" />
        <span className="font-display text-lg font-semibold">WattPilot</span>
      </div>

      <CardHeader className="px-0">
        <CardTitle className="text-xl">Sign in</CardTitle>
        <CardDescription>Charge your EV when electricity is cheapest.</CardDescription>
      </CardHeader>
      <CardContent className="px-0">
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
                    <Input type="password" autoComplete="current-password" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="rememberMe"
              render={({ field }) => (
                <FormItem className="flex flex-row items-center gap-2 space-y-0">
                  <FormControl>
                    <input
                      type="checkbox"
                      className="border-input text-primary focus-visible:ring-ring size-4 rounded border focus-visible:ring-2 focus-visible:outline-none"
                      name={field.name}
                      ref={field.ref}
                      checked={field.value}
                      onBlur={field.onBlur}
                      onChange={(event) => field.onChange(event.target.checked)}
                    />
                  </FormControl>
                  <FormLabel className="font-normal">Keep me signed in on this device</FormLabel>
                </FormItem>
              )}
            />

            <Button type="submit" className="w-full" disabled={form.formState.isSubmitting}>
              Sign in
            </Button>
          </form>
        </Form>

        <p className="text-muted-foreground mt-4 text-center text-sm">
          No account?{' '}
          <Link to="/signup" className="text-foreground font-medium underline underline-offset-4">
            Create one
          </Link>
        </p>
      </CardContent>
    </AuthShell>
  )
}
