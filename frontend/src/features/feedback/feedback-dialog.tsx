import { useEffect, useRef, useState } from 'react'
import { XIcon } from 'lucide-react'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import { errorMessage } from '@/lib/error-message'
import { FEEDBACK_MAX_LENGTH, sendFeedback } from './api'

export interface FeedbackDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function FeedbackDialog({ open, onOpenChange }: FeedbackDialogProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const [message, setMessage] = useState('')
  const [sending, setSending] = useState(false)

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

  const trimmed = message.trim()

  const onSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!trimmed) {
      return
    }
    setSending(true)
    try {
      await sendFeedback(trimmed)
      toast.success('Thank you for your feedback!')
      setMessage('')
      onOpenChange(false)
    } catch (error) {
      toast.error(errorMessage(error))
    } finally {
      setSending(false)
    }
  }

  return (
    <dialog
      ref={dialogRef}
      onClose={() => onOpenChange(false)}
      onClick={(event) => {
        if (event.target === dialogRef.current) {
          onOpenChange(false)
        }
      }}
      className="bg-card text-card-foreground border-border m-auto w-[calc(100%-2rem)] max-w-md rounded-2xl border p-0 shadow-xl backdrop:bg-black/50"
    >
      <form onSubmit={onSubmit} className="space-y-4 p-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 className="font-display text-xl font-semibold tracking-tight">
              Welcome to your feedback!
            </h2>
            <p className="text-muted-foreground mt-1 text-sm">
              Tell me what you liked, what confused you, or what is missing.
            </p>
          </div>
          <Button
            type="button"
            variant="ghost"
            size="icon"
            aria-label="Close"
            onClick={() => onOpenChange(false)}
          >
            <XIcon />
          </Button>
        </div>

        <div className="space-y-1.5">
          <textarea
            value={message}
            onChange={(event) => setMessage(event.target.value)}
            maxLength={FEEDBACK_MAX_LENGTH}
            rows={6}
            aria-label="Your feedback"
            placeholder="Write your feedback here…"
            className="placeholder:text-muted-foreground border-input focus-visible:border-ring focus-visible:ring-ring/50 w-full resize-y rounded-md border bg-transparent px-3 py-2 text-base shadow-xs outline-none focus-visible:ring-[3px] md:text-sm"
          />
          <p className="text-muted-foreground text-right text-xs">
            {message.length}/{FEEDBACK_MAX_LENGTH}
          </p>
        </div>

        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button type="submit" disabled={!trimmed || sending}>
            {sending ? 'Sending…' : 'Send feedback'}
          </Button>
        </div>
      </form>
    </dialog>
  )
}
