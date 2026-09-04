import { useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import { useAuth } from '@/features/auth/use-auth'
import { cn } from '@/lib/utils'

const NAV_ITEMS = [
  { to: '/evs', label: 'My EVs', match: '/evs' },
  { to: '/charging/schedules', label: 'Charging', match: '/charging' },
] as const

export function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [loggingOut, setLoggingOut] = useState(false)

  const handleLogout = async () => {
    setLoggingOut(true)
    try {
      await logout()
      navigate('/login', { replace: true })
    } catch {
      toast.error('Could not log out. Please try again.')
      setLoggingOut(false)
    }
  }

  return (
    <div className="min-h-svh">
      <header className="border-b">
        <div className="mx-auto flex h-14 max-w-4xl items-center justify-between px-4">
          <Link to="/evs" className="flex items-center gap-2 font-semibold">
            <img src="/favicon.svg" alt="" className="size-6" />
            WattPilot
          </Link>
          <nav className="flex items-center gap-1">
            {NAV_ITEMS.map((item) => {
              const active = location.pathname.startsWith(item.match)
              return (
                <NavLink
                  key={item.to}
                  to={item.to}
                  className={cn(
                    'rounded-md px-3 py-1.5 text-sm font-medium transition-colors',
                    active
                      ? 'bg-secondary text-secondary-foreground'
                      : 'text-muted-foreground hover:text-foreground',
                  )}
                >
                  {item.label}
                </NavLink>
              )
            })}
          </nav>
          <div className="flex items-center gap-3">
            {user ? (
              <span className="text-muted-foreground hidden text-sm sm:inline" title={user.email}>
                {user.email}
              </span>
            ) : null}
            <Button variant="outline" size="sm" onClick={handleLogout} disabled={loggingOut}>
              Log out
            </Button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-4xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  )
}
