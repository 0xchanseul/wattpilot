import { AlertCircleIcon } from 'lucide-react'

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { errorMessage } from '@/lib/error-message'
import { cn } from '@/lib/utils'

export function ApiErrorAlert({
  error,
  title = 'Something went wrong',
  className,
}: {
  error: unknown
  title?: string
  className?: string
}) {
  return (
    <Alert variant="destructive" className={cn(className)}>
      <AlertCircleIcon />
      <AlertTitle>{title}</AlertTitle>
      <AlertDescription>{errorMessage(error)}</AlertDescription>
    </Alert>
  )
}
