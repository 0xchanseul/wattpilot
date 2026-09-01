import { Badge } from '@/components/ui/badge'
import type { EvStatus } from '../types'

export function EvStatusBadge({ status }: { status: EvStatus }) {
  if (status === 'ACTIVE') {
    return <Badge variant="secondary">Active</Badge>
  }
  return <Badge variant="outline">Deactivated</Badge>
}
