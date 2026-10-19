import type { ReactNode } from 'react'
import { CalendarClockIcon, EyeIcon, PercentIcon, UserPlusIcon } from 'lucide-react'

import { Card, CardContent } from '@/components/ui/card'
import type { AnalyticsSummary } from '../types'

const count = new Intl.NumberFormat('en-GB')

function percent(rate: number | null): string {
  return rate === null ? '–' : `${(rate * 100).toFixed(1)}%`
}

export function AdminSummaryCards({ summary }: { summary: AnalyticsSummary }) {
  return (
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
      <Stat
        icon={<EyeIcon className="size-3.5" />}
        label="Visitors"
        value={count.format(summary.visitorDays)}
        hint="Distinct per day, summed over the range"
      />
      <Stat
        icon={<UserPlusIcon className="size-3.5" />}
        label="New sign-ups"
        value={count.format(summary.newSignups)}
        hint={`${count.format(summary.totalSignups)} accounts in total`}
      />
      <Stat
        icon={<PercentIcon className="size-3.5" />}
        label="Sign-up conversion"
        value={
          summary.signupsPer100VisitorDays === null ? '–' : summary.signupsPer100VisitorDays.toFixed(1)
        }
        hint="Sign-ups per 100 visitors (approximate)"
      />
      <Stat
        icon={<CalendarClockIcon className="size-3.5" />}
        label="Schedules created"
        value={count.format(summary.schedulesCreated)}
        hint={`${count.format(summary.usersWithSchedule)} of ${count.format(summary.totalUsers)} users have made one (${percent(summary.activationRate)})`}
      />
    </div>
  )
}

function Stat({ icon, label, value, hint }: { icon: ReactNode; label: string; value: string; hint: string }) {
  return (
    <Card>
      <CardContent className="space-y-1">
        <p className="text-muted-foreground flex items-center gap-1.5 text-xs">
          {icon} {label}
        </p>
        <p className="text-2xl font-semibold tabular-nums">{value}</p>
        <p className="text-muted-foreground text-xs">{hint}</p>
      </CardContent>
    </Card>
  )
}
