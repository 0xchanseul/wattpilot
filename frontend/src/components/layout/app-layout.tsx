import { useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router'
import { InfoIcon, LogOutIcon, MessageSquareIcon, UserIcon } from 'lucide-react'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import { useAuth } from '@/features/auth/use-auth'
import { FeedbackDialog } from '@/features/feedback/feedback-dialog'
import { cn } from '@/lib/utils'

const NAV_ITEMS = [
  { to: '/dashboard', label: 'Dashboard', match: '/dashboard' },
  { to: '/evs', label: 'My EVs', match: '/evs' },
  { to: '/charging/schedules', label: 'Charging', match: '/charging' },
  { to: '/charging/history', label: 'History', match: '/charging/history' },
  { to: '/savings', label: 'Savings', match: '/savings' },
] as const

export function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [loggingOut, setLoggingOut] = useState(false)
  const [feedbackOpen, setFeedbackOpen] = useState(false)

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
      {user?.demo ? (
        <div className="bg-primary/10 text-foreground border-primary/20 border-b px-4 py-2 text-center text-sm">
          <InfoIcon className="mr-1.5 inline size-4 align-text-bottom" />
          You are using a demo account. Your data is temporary and is deleted automatically.{' '}
          <button
            type="button"
            onClick={() => setFeedbackOpen(true)}
            className="text-primary font-medium underline underline-offset-2"
          >
            Leave feedback
          </button>
        </div>
      ) : null}
      <header className="border-border/70 bg-background/85 sticky top-0 z-40 border-b backdrop-blur-md">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-4 px-4 sm:px-6">
          <Link to="/dashboard" className="flex shrink-0 items-center gap-2">
            <img src="/favicon.svg" alt="" className="size-7" />
            <span className="font-display text-lg font-semibold tracking-tight">WattPilot</span>
          </Link>

          <nav className="bg-muted/60 hidden items-center gap-1 rounded-full p-1 sm:flex">
            {NAV_ITEMS.map((item) => {
              // Longest matching prefix wins, so "/charging/history" activates History, not Charging.
              const activeMatch = NAV_ITEMS.reduce(
                (best, candidate) =>
                  location.pathname.startsWith(candidate.match) &&
                  candidate.match.length > best.length
                    ? candidate.match
                    : best,
                '',
              )
              const active = item.match === activeMatch
              return (
                <NavLink
                  key={item.to}
                  to={item.to}
                  className={cn(
                    'rounded-full px-3.5 py-1.5 text-sm font-medium transition-colors',
                    active
                      ? 'bg-primary text-primary-foreground shadow-sm'
                      : 'text-muted-foreground hover:text-foreground',
                  )}
                >
                  {item.label}
                </NavLink>
              )
            })}
          </nav>

          <div className="flex min-w-0 items-center gap-3">
            {user ? (
              <Link
                to="/profile"
                className="hover:bg-muted/60 flex min-w-0 items-center gap-2 rounded-full p-1 pr-3 transition-colors max-sm:hidden"
                title={user.email}
              >
                <span className="bg-aurora-gradient text-fjord flex size-8 shrink-0 items-center justify-center rounded-full text-sm font-semibold">
                  {user.name.charAt(0).toUpperCase()}
                </span>
                <span className="truncate text-sm font-medium">{user.name}</span>
              </Link>
            ) : null}
            <Button variant="outline" size="icon" className="sm:hidden" asChild>
              <Link to="/profile" aria-label="Profile">
                <UserIcon />
              </Link>
            </Button>
            <Button
              variant="outline"
              size="icon"
              className="sm:hidden"
              onClick={handleLogout}
              disabled={loggingOut}
              aria-label="Log out"
            >
              <LogOutIcon />
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={handleLogout}
              disabled={loggingOut}
              className="hidden sm:inline-flex"
            >
              <LogOutIcon /> Log out
            </Button>
          </div>
        </div>

        <nav className="border-border/70 flex items-center gap-1 overflow-x-auto border-t px-4 py-2 sm:hidden">
          {NAV_ITEMS.map((item) => {
            const activeMatch = NAV_ITEMS.reduce(
              (best, candidate) =>
                location.pathname.startsWith(candidate.match) &&
                candidate.match.length > best.length
                  ? candidate.match
                  : best,
              '',
            )
            const active = item.match === activeMatch
            return (
              <NavLink
                key={item.to}
                to={item.to}
                className={cn(
                  'shrink-0 rounded-full px-3 py-1.5 text-sm font-medium transition-colors',
                  active
                    ? 'bg-primary text-primary-foreground'
                    : 'text-muted-foreground hover:text-foreground bg-muted/60',
                )}
              >
                {item.label}
              </NavLink>
            )
          })}
        </nav>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-8 sm:px-6">
        <Outlet />
      </main>
      <Button
        type="button"
        onClick={() => setFeedbackOpen(true)}
        aria-label="Send feedback"
        className="fixed right-4 bottom-4 z-40 rounded-full shadow-lg sm:right-6 sm:bottom-6"
      >
        <MessageSquareIcon />
        <span className="max-sm:hidden">Feedback</span>
      </Button>
      <FeedbackDialog open={feedbackOpen} onOpenChange={setFeedbackOpen} />
    </div>
  )
}
