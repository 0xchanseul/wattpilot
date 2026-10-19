import { Button } from '@/components/ui/button'
import type { RangePreset } from '../range'

const PRESETS: { label: string; value: RangePreset }[] = [
  { label: 'Today', value: 'today' },
  { label: 'Last 7 days', value: '7d' },
  { label: 'Last 30 days', value: '30d' },
  { label: 'All time', value: 'all' },
]

export function RangeFilter({ value, onChange }: { value: RangePreset; onChange: (preset: RangePreset) => void }) {
  return (
    <div className="flex flex-wrap gap-1">
      {PRESETS.map((preset) => (
        <Button
          key={preset.value}
          type="button"
          size="sm"
          variant={value === preset.value ? 'default' : 'outline'}
          onClick={() => onChange(preset.value)}
        >
          {preset.label}
        </Button>
      ))}
    </div>
  )
}
