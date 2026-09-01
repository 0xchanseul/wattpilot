import { QueryClient } from '@tanstack/react-query'
import { ApiError } from '@/lib/api-error'

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        retry: (failureCount, error) => {
          // A 4xx will not fix itself on retry; only retry genuine network failures, and only once.
          if (error instanceof ApiError && error.status !== 0) {
            return false
          }
          return failureCount < 1
        },
      },
      mutations: {
        retry: false,
      },
    },
  })
}
