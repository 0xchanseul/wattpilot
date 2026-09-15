import type { ReactNode } from 'react'
import { Link } from 'react-router'

import { ChargingCarIllustration } from '@/features/dashboard/components/charging-car-illustration'

/**
 * Shared chrome for the Login and Sign-up pages: a fjord-midnight brand panel (reusing the same
 * charging illustration as the dashboard hero) next to the auth form, so the app's very first
 * screen already carries the aurora identity instead of a plain centered card.
 */
export function AuthShell({ tagline, children }: { tagline: string; children: ReactNode }) {
  return (
    <div className="grid min-h-svh lg:grid-cols-2">
      <div className="from-fjord to-deep-water relative hidden flex-col justify-between overflow-hidden bg-gradient-to-br p-10 text-white lg:flex">
        <Link className="flex items-center gap-2" to="/login">
          <img src="/favicon.svg" alt="" className="size-8" />
          <span className="font-display text-xl font-semibold">WattPilot</span>
        </Link>

        <div className="relative -mx-6">
          <div className="aspect-[16/9]">
            <ChargingCarIllustration charging />
          </div>
        </div>

        <div className="max-w-sm space-y-2">
          <p className="font-display text-2xl leading-snug font-semibold">{tagline}</p>
          <p className="text-sm text-white/70">
            WattPilot watches Norwegian hourly electricity prices and charges your EV in the
            cheapest window automatically.
          </p>
        </div>
      </div>

      <div className="flex items-center justify-center px-4 py-10">
        <div className="w-full max-w-sm">{children}</div>
      </div>
    </div>
  )
}
