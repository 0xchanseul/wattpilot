import { apiRequest } from '@/lib/api-client'
import { ApiError } from '@/lib/api-error'
import type { PageResponse } from '@/types/api'
import type {
  CreateEvInput,
  Ev,
  ListEvsParams,
  UpdateEvInput,
  VehicleConnectCandidates,
  VehicleConnection,
  VehicleModel,
  VehicleTelemetry,
} from './types'

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

export function listVehicleModels(query?: string): Promise<VehicleModel[]> {
  return apiRequest<VehicleModel[]>('/vehicle-models', { query: { q: query } })
}

export function getVehicleConnectUrl(evId: number): Promise<{ url: string }> {
  return apiRequest<{ url: string }>(`/evs/${evId}/vehicle-connection/connect-url`, { method: 'POST' })
}

export function getVehicleConnectCandidates(input: {
  state: string
  smartcarUserId: string
}): Promise<VehicleConnectCandidates> {
  return apiRequest<VehicleConnectCandidates>('/vehicle-connections/candidates', {
    method: 'POST',
    body: input,
  })
}

export function linkVehicleConnection(
  evId: number,
  input: { state: string; smartcarUserId: string; smartcarVehicleId: string },
): Promise<VehicleConnection> {
  return apiRequest<VehicleConnection>(`/evs/${evId}/vehicle-connection`, { method: 'POST', body: input })
}

/** Resolves to `null` (not an error) when the EV has no vehicle connection. */
export async function getVehicleConnection(evId: number): Promise<VehicleConnection | null> {
  try {
    return await apiRequest<VehicleConnection>(`/evs/${evId}/vehicle-connection`)
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return null
    }
    throw error
  }
}

export function getVehicleTelemetry(evId: number): Promise<VehicleTelemetry> {
  return apiRequest<VehicleTelemetry>(`/evs/${evId}/vehicle-connection/telemetry`)
}

export function disconnectVehicleConnection(evId: number): Promise<void> {
  return apiRequest<void>(`/evs/${evId}/vehicle-connection`, { method: 'DELETE' })
}
