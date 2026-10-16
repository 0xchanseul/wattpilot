import { useEffect, useRef, useState } from 'react'
import { XIcon } from 'lucide-react'

import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { errorMessage } from '@/lib/error-message'

export interface DeleteAccountDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Rejects with the backend's error, which is shown inside the dialog. */
  onConfirm: (password: string) => Promise<void>
}

export function DeleteAccountDialog({ open, onOpenChange, onConfirm }: DeleteAccountDialogProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const [password, setPassword] = useState('')
  const [deleting, setDeleting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // The native dialog provides the focus trap, Escape handling and backdrop.
  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) {
      return
    }
    if (open && !dialog.open) {
      dialog.showModal()
    } else if (!open && dialog.open) {
      dialog.close()
    }
  }, [open])

  const close = () => {
    setPassword('')
    setError(null)
    onOpenChange(false)
  }

  const onSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!password) {
      return
    }
    setDeleting(true)
    setError(null)
    try {
      await onConfirm(password)
    } catch (caught) {
      setError(errorMessage(caught))
    } finally {
      setDeleting(false)
    }
  }

  return (
    <dialog
      ref={dialogRef}
      onClose={close}
      onClick={(event) => {
        if (event.target === dialogRef.current && !deleting) {
          close()
        }
      }}
      className="bg-card text-card-foreground border-border m-auto w-[calc(100%-2rem)] max-w-md rounded-2xl border p-0 shadow-xl backdrop:bg-black/50"
    >
      <form onSubmit={onSubmit} className="space-y-4 p-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 className="font-display text-xl font-semibold tracking-tight">Delete your account?</h2>
            <p className="text-muted-foreground mt-1 text-sm">
              This permanently deletes your account, your EVs, charging schedules and history, and
              any connected vehicle. It cannot be undone.
            </p>
          </div>
          <Button type="button" variant="ghost" size="icon" aria-label="Close" onClick={close}>
            <XIcon />
          </Button>
        </div>

        {error ? (
          <Alert variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        ) : null}

        <div className="space-y-1.5">
          <Label htmlFor="delete-account-password">Enter your password to confirm</Label>
          <Input
            id="delete-account-password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
        </div>

        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={close} disabled={deleting}>
            Cancel
          </Button>
          <Button type="submit" variant="destructive" disabled={!password || deleting}>
            {deleting ? 'Deleting…' : 'Delete account'}
          </Button>
        </div>
      </form>
    </dialog>
  )
}
