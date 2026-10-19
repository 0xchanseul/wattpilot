import { useState, type FormEvent } from 'react'
import { LockIcon } from 'lucide-react'

import { ApiErrorAlert } from '@/components/api-error-alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useAdminLoginMutation } from '../queries'

export function AdminLoginForm() {
  const [password, setPassword] = useState('')
  const login = useAdminLoginMutation()

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    login.mutate(password)
  }

  return (
    <div className="flex min-h-svh items-center justify-center px-4">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <LockIcon className="text-muted-foreground size-4" />
            Admin
          </CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="admin-password">Password</Label>
              <Input
                id="admin-password"
                type="password"
                autoComplete="current-password"
                autoFocus
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
            </div>
            {login.isError ? <ApiErrorAlert error={login.error} title="Could not sign in" /> : null}
            <Button type="submit" className="w-full" disabled={!password || login.isPending}>
              {login.isPending ? 'Checking…' : 'Sign in'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
