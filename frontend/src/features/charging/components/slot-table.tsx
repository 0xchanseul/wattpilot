import { formatKwh, formatNok, formatOrePerKwh, formatTime } from '@/lib/format'
import type { ChargingPlanSlot } from '../types'

/** The hourly price slots that make up a continuous charging window. */
export function SlotTable({ slots }: { slots: ChargingPlanSlot[] }) {
  const totalEnergy = slots.reduce((sum, slot) => sum + slot.plannedEnergyKwh, 0)
  const totalCost = slots.reduce((sum, slot) => sum + slot.expectedCostNok, 0)

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="text-muted-foreground border-b text-left">
            <th className="py-2 pr-4 font-medium">Time</th>
            <th className="py-2 pr-4 font-medium">Price</th>
            <th className="py-2 pr-4 text-right font-medium">Energy</th>
            <th className="py-2 text-right font-medium">Cost</th>
          </tr>
        </thead>
        <tbody>
          {slots.map((slot) => (
            <tr key={slot.startsAt} className="border-b last:border-0">
              <td className="py-2 pr-4 whitespace-nowrap">
                {formatTime(slot.startsAt)}&ndash;{formatTime(slot.endsAt)}
              </td>
              <td className="py-2 pr-4 whitespace-nowrap">{formatOrePerKwh(slot.pricePerKwh)}</td>
              <td className="py-2 pr-4 text-right whitespace-nowrap">
                {formatKwh(slot.plannedEnergyKwh)}
              </td>
              <td className="py-2 text-right whitespace-nowrap">{formatNok(slot.expectedCostNok)}</td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr className="font-medium">
            <td className="py-2 pr-4">Total</td>
            <td className="py-2 pr-4" />
            <td className="py-2 pr-4 text-right whitespace-nowrap">{formatKwh(totalEnergy)}</td>
            <td className="py-2 text-right whitespace-nowrap">{formatNok(totalCost)}</td>
          </tr>
        </tfoot>
      </table>
    </div>
  )
}
