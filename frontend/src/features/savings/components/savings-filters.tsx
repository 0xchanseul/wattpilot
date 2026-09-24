import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import type { Ev } from '@/features/ev/types'
import { cn } from '@/lib/utils'

export type RangePreset = '30d' | '3m' | '6m' | '12m'

const PRESETS: { label: string; value: RangePreset }[] = [
  { label: '30 days', value: '30d' },
  { label: '3 months', value: '3m' },
  { label: '6 months', value: '6m' },
  { label: '12 months', value: '12m' },
]

interface SavingsFiltersProps {
  from: string
  to: string
  evId: number | undefined
  evs: Ev[]
  activePreset: RangePreset | null
  onPreset: (preset: RangePreset) => void
  onFromChange: (date: string) => void
  onToChange: (date: string) => void
  onEvChange: (evId: number | undefined) => void
}

export function SavingsFilters({
  from,
  to,
  evId,
  evs,
  activePreset,
  onPreset,
  onFromChange,
  onToChange,
  onEvChange,
}: SavingsFiltersProps) {
  return (
    <div className="flex flex-wrap items-end gap-4">
      <div className="flex flex-wrap gap-1">
        {PRESETS.map((preset) => (
          <Button
            key={preset.value}
            type="button"
            size="sm"
            variant={activePreset === preset.value ? 'default' : 'outline'}
            onClick={() => onPreset(preset.value)}
          >
            {preset.label}
          </Button>
        ))}
      </div>

      <div className="flex items-end gap-2">
        <div className="space-y-1">
          <Label htmlFor="savings-from" className="text-muted-foreground text-xs">
            From
          </Label>
          <Input
            id="savings-from"
            type="date"
            value={from}
            max={to}
            onChange={(event) => onFromChange(event.target.value)}
            className="w-[150px]"
          />
        </div>
        <div className="space-y-1">
          <Label htmlFor="savings-to" className="text-muted-foreground text-xs">
            To
          </Label>
          <Input
            id="savings-to"
            type="date"
            value={to}
            min={from}
            onChange={(event) => onToChange(event.target.value)}
            className="w-[150px]"
          />
        </div>
      </div>

      <div className={cn('space-y-1', evs.length === 0 && 'hidden')}>
        <Label className="text-muted-foreground text-xs">EV</Label>
        <Select
          value={evId ? String(evId) : 'ALL'}
          onValueChange={(value) => onEvChange(value === 'ALL' ? undefined : Number(value))}
        >
          <SelectTrigger className="w-[160px]">
            <SelectValue placeholder="All EVs" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">All EVs</SelectItem>
            {evs.map((ev) => (
              <SelectItem key={ev.id} value={String(ev.id)}>
                {ev.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
    </div>
  )
}
