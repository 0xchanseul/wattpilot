import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseQueryOptions,
} from '@tanstack/react-query'

import type { PageResponse } from '@/types/api'
import { createEv, deactivateEv, getEv, listEvs, updateEv } from './api'
import type { CreateEvInput, Ev, ListEvsParams, UpdateEvInput } from './types'

export const evKeys = {
  all: ['evs'] as const,
  list: (params: ListEvsParams) => ['evs', 'list', params] as const,
  detail: (evId: number) => ['evs', 'detail', evId] as const,
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
