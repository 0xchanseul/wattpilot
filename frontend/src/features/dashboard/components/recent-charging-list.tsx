import { Link } from 'react-router'
import { CarIcon, HistoryIcon } from 'lucide-react'

import { formatDate, formatKwh, formatNok } from '@/lib/format'
import { listOrigin } from '@/lib/navigation'
import type { DashboardRecentSession } from '../types'

const DASHBOARD_ORIGIN = listOrigin('/dashboard', 'Dashboard')

/** The last 3 completed charges — a summary, not a re-implementation of the History list. */
export function RecentChargingList({ sessions }: { sessions: DashboardRecentSession[] }) {
  if (sessions.length === 0) {
    return (
      <div className="text-muted-foreground flex flex-col items-center gap-2 py-10 text-center text-sm">
        <HistoryIcon className="size-6" />
        No completed charges yet.
      </div>
    )
  }

  return (
    <ul className="divide-y">
      {sessions.map((session) => (
        <li key={session.sessionId}>
          <Link
            to={`/charging/history/${session.sessionId}`}
            state={DASHBOARD_ORIGIN}
            className="hover:bg-accent/50 -mx-6 flex items-center justify-between gap-4 px-6 py-3 transition-colors"
          >
            <div className="min-w-0 space-y-0.5">
              <p className="flex items-center gap-1.5 truncate text-sm font-medium">
                <CarIcon className="size-3.5 shrink-0" />
                {session.evName}
              </p>
              <p className="text-muted-foreground text-xs">
                {session.endedAt ? formatDate(session.endedAt) : '—'} · {formatKwh(session.actualEnergyKwh)}
              </p>
            </div>
            <div className="shrink-0 text-right text-sm">
              <p className="font-medium tabular-nums">{formatNok(session.actualCostNok)}</p>
              <p className="text-chart-2 text-xs tabular-nums">
                −{formatNok(session.realizedSavingsNok)}
              </p>
            </div>
          </Link>
        </li>
      ))}
    </ul>
  )
}
