import { apiRequest } from '@/lib/api-client'
import type { PageResponse } from '@/types/api'
import type { CreateEvInput, Ev, ListEvsParams, UpdateEvInput } from './types'

export function listEvs(params: ListEvsParams = {}): Promise<PageResponse<Ev>> {
  return apiRequest<PageResponse<Ev>>('/evs', {
    query: { status: params.status, page: params.page, size: params.size },
  })
}

export function getEv(evId: number): Promise<Ev> {
  return apiRequest<Ev>(`/evs/${evId}`)
}

export function createEv(input: CreateEvInput): Promise<Ev> {
  return apiRequest<Ev>('/evs', { method: 'POST', body: input })
}

export function updateEv(evId: number, input: UpdateEvInput): Promise<Ev> {
  return apiRequest<Ev>(`/evs/${evId}`, { method: 'PATCH', body: input })
}

export function deactivateEv(evId: number): Promise<void> {
  return apiRequest<void>(`/evs/${evId}`, { method: 'DELETE' })
}
