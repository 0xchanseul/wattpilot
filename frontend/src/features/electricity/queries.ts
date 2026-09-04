import { useQuery } from '@tanstack/react-query'

import { listElectricityPrices } from './api'
import type { ListElectricityPricesParams } from './types'

export const electricityKeys = {
  all: ['electricity-prices'] as const,
  list: (params: ListElectricityPricesParams) =>
    ['electricity-prices', 'list', params] as const,
}

/**
 * Hourly prices for a window. Used to draw the price timeline behind a charging preview, so a failure
 * here is non-fatal: the caller still has the candidate's own slot prices.
 */
export function useElectricityPricesQuery(
  params: ListElectricityPricesParams | null,
) {
  return useQuery({
    queryKey: electricityKeys.list(params ?? { priceArea: 'NO1', from: '', to: '' }),
    queryFn: () => listElectricityPrices(params as ListElectricityPricesParams),
    enabled: params !== null,
    staleTime: 5 * 60_000,
    retry: false,
  })
}
