import { Badge } from '@/components/ui/badge'
import type { ScheduleStatus } from '../types'

const LABELS: Record<ScheduleStatus, string> = {
  CREATED: 'Scheduled',
  WAITING: 'Waiting',
  IN_PROGRESS: 'Charging',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
  FAILED: 'Failed',
}

const VARIANTS: Record<ScheduleStatus, 'default' | 'secondary' | 'outline' | 'destructive'> = {
  CREATED: 'secondary',
  WAITING: 'secondary',
  IN_PROGRESS: 'default',
  COMPLETED: 'default',
  CANCELLED: 'outline',
  FAILED: 'destructive',
}

export function ScheduleStatusBadge({ status }: { status: ScheduleStatus }) {
  return <Badge variant={VARIANTS[status]}>{LABELS[status]}</Badge>
}
