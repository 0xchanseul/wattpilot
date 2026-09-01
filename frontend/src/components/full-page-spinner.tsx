import { Loader2Icon } from 'lucide-react'

export function FullPageSpinner() {
  return (
    <div className="flex min-h-svh items-center justify-center">
      <Loader2Icon className="text-muted-foreground size-6 animate-spin" />
      <span className="sr-only">Loading</span>
    </div>
  )
}
