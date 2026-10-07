import { apiRequest } from '@/lib/api-client'

export const FEEDBACK_MAX_LENGTH = 2000

export function sendFeedback(message: string): Promise<void> {
  return apiRequest<void>('/feedback', { method: 'POST', body: { message } })
}
