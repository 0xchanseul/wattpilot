import type { ReactNode } from 'react'
import { Link } from 'react-router'

import evChargingImage from '@/assets/ev-charging.png'

/**
 * Shared chrome for the Login and Sign-up pages: a fjord-midnight brand panel (showing the same
 * charging vehicle render as the dashboard hero) next to the auth form, so the app's very first
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

        <div className="flex justify-center">
          <img
            src={evChargingImage}
            alt="Vehicle charging"
            className="w-full max-w-lg object-contain select-none"
            draggable={false}
          />
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
