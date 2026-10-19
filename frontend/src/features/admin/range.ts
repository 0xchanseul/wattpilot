import { isoDateMinus, todayIso } from '@/lib/format'

export type RangePreset = 'today' | '7d' | '30d' | 'all'

/** Calendar days in Europe/Oslo, matching the backend's day boundary. */
export function presetRange(preset: RangePreset): { from?: string; to: string } {
  const to = todayIso()
  switch (preset) {
    case 'today':
      return { from: to, to }
    case '7d':
      return { from: isoDateMinus(to, 'days', 6), to }
    case '30d':
      return { from: isoDateMinus(to, 'days', 29), to }
    case 'all':
      return { to }
  }
}
