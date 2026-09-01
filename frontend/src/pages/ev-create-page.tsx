import { Link, useNavigate } from 'react-router'
import { ChevronLeftIcon } from 'lucide-react'
import { toast } from 'sonner'

import { EvForm } from '@/features/ev/components/ev-form'
import { useCreateEvMutation } from '@/features/ev/queries'
import type { CreateEvInput } from '@/features/ev/types'

export function EvCreatePage() {
  const navigate = useNavigate()
  const mutation = useCreateEvMutation()

  const handleSubmit = async (payload: CreateEvInput) => {
    const ev = await mutation.mutateAsync(payload)
    toast.success(`${ev.name} registered`)
    navigate(`/evs/${ev.id}`, { replace: true })
  }

  return (
    <div className="space-y-6">
      <Link
        to="/evs"
        className="text-muted-foreground hover:text-foreground inline-flex items-center gap-1 text-sm"
      >
        <ChevronLeftIcon className="size-4" /> My EVs
      </Link>
      <div>
        <h1 className="text-2xl font-semibold">Register an EV</h1>
        <p className="text-muted-foreground text-sm">Enter the battery and charging figures by hand.</p>
      </div>
      <EvForm submitLabel="Register EV" onSubmit={handleSubmit} onCancel={() => navigate('/evs')} />
    </div>
  )
}
