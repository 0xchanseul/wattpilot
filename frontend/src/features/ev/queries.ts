import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseQueryOptions,
} from '@tanstack/react-query'

import type { PageResponse } from '@/types/api'
import {
  createEv,
  deactivateEv,
  disconnectVehicleConnection,
  getEv,
  getVehicleConnectCandidates,
  getVehicleConnectUrl,
  getVehicleConnection,
  getVehicleTelemetry,
  linkVehicleConnection,
  listEvs,
  listVehicleModels,
  updateEv,
} from './api'
import type { CreateEvInput, Ev, ListEvsParams, UpdateEvInput } from './types'

export const evKeys = {
  all: ['evs'] as const,
  list: (params: ListEvsParams) => ['evs', 'list', params] as const,
  detail: (evId: number) => ['evs', 'detail', evId] as const,
}

export const vehicleModelKeys = {
  all: ['vehicle-models'] as const,
}

/** Presets for the EV registration form's model picker; the full list is small enough to fetch at once. */
export function useVehicleModelsQuery() {
  return useQuery({
    queryKey: vehicleModelKeys.all,
    queryFn: () => listVehicleModels(),
    staleTime: Infinity,
  })
}

export function useEvsQuery(
  params: ListEvsParams = {},
  options?: Partial<UseQueryOptions<PageResponse<Ev>>>,
) {
  return useQuery({
    queryKey: evKeys.list(params),
    queryFn: () => listEvs(params),
    ...options,
  })
}

export function useEvQuery(evId: number) {
  return useQuery({
    queryKey: evKeys.detail(evId),
    queryFn: () => getEv(evId),
    enabled: Number.isFinite(evId),
  })
}

export function useCreateEvMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: CreateEvInput) => createEv(input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: evKeys.all })
    },
  })
}

export function useUpdateEvMutation(evId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: UpdateEvInput) => updateEv(evId, input),
    onSuccess: (updated) => {
      queryClient.setQueryData(evKeys.detail(evId), updated)
      void queryClient.invalidateQueries({ queryKey: evKeys.all })
    },
  })
}

export function useDeactivateEvMutation(evId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => deactivateEv(evId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: evKeys.all })
    },
  })
}

// --- Vehicle connection (Smartcar, read-only, V1.5) -------------------------

const vehicleConnectionKey = (evId: number) => [...evKeys.detail(evId), 'vehicle-connection'] as const
const vehicleTelemetryKey = (evId: number) => [...vehicleConnectionKey(evId), 'telemetry'] as const

export function useVehicleConnectionQuery(evId: number) {
  return useQuery({
    queryKey: vehicleConnectionKey(evId),
    queryFn: () => getVehicleConnection(evId),
    enabled: Number.isFinite(evId),
  })
}

/** Live telemetry, refetched at most every 15s while the tab is active; only enabled once a
 * connection is known to exist, so an unlinked EV never calls this endpoint. */
export function useVehicleTelemetryQuery(evId: number, connected: boolean) {
  return useQuery({
    queryKey: vehicleTelemetryKey(evId),
    queryFn: () => getVehicleTelemetry(evId),
    enabled: Number.isFinite(evId) && connected,
    staleTime: 15_000,
  })
}

export function useVehicleConnectUrlMutation(evId: number) {
  return useMutation({ mutationFn: () => getVehicleConnectUrl(evId) })
}

export function useVehicleConnectCandidatesMutation() {
  return useMutation({
    mutationFn: (input: { state: string; smartcarUserId: string }) => getVehicleConnectCandidates(input),
  })
}

export function useLinkVehicleConnectionMutation(evId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { state: string; smartcarUserId: string; smartcarVehicleId: string }) =>
      linkVehicleConnection(evId, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: evKeys.all })
    },
  })
}

export function useDisconnectVehicleMutation(evId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => disconnectVehicleConnection(evId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: evKeys.all })
    },
  })
}
