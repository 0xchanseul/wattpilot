import { apiRequest } from '@/lib/api-client'
import type { ElectricityPriceListResponse, ListElectricityPricesParams } from './types'

export function listElectricityPrices(
  params: ListElectricityPricesParams,
): Promise<ElectricityPriceListResponse> {
  return apiRequest<ElectricityPriceListResponse>('/electricity-prices', {
    query: { priceArea: params.priceArea, from: params.from, to: params.to },
  })
}
