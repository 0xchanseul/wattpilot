import { Badge } from '@/components/ui/badge'
import type { HistoryStatus } from '../types'

const META: Record<HistoryStatus, { label: string; variant: 'default' | 'destructive' }> = {
  COMPLETED: { label: 'Completed', variant: 'default' },
  FAILED: { label: 'Failed', variant: 'destructive' },
}

export function HistoryStatusBadge({ status }: { status: HistoryStatus }) {
  const { label, variant } = META[status]
  return <Badge variant={variant}>{label}</Badge>
}
