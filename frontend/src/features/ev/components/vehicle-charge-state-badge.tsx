import { BatteryChargingIcon, HelpCircleIcon, PlugIcon, PlugZapIcon } from 'lucide-react'

import { Badge } from '@/components/ui/badge'

/**
 * Derived from `isPluggedIn`/`isCharging` (both nullable when Smartcar doesn't have the signal).
 * Not a backend enum - just a display convenience for {@link VehicleTelemetry}.
 */
export function VehicleChargeStateBadge({
  isPluggedIn,
  isCharging,
}: {
  isPluggedIn: boolean | null
  isCharging: boolean | null
}) {
  if (isCharging === true) {
    return (
      <Badge variant="default">
        <BatteryChargingIcon /> Charging
      </Badge>
    )
  }
  if (isPluggedIn === true) {
    return (
      <Badge variant="secondary">
        <PlugZapIcon /> Plugged in
      </Badge>
    )
  }
  if (isPluggedIn === false) {
    return (
      <Badge variant="outline">
        <PlugIcon /> Not plugged in
      </Badge>
    )
  }
  return (
    <Badge variant="outline">
      <HelpCircleIcon /> Unknown
    </Badge>
  )
}
