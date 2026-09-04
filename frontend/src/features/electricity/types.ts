import type { PriceArea } from '@/types/api'

/** One hourly electricity price. Mirrors the `ElectricityPrice` schema in docs/openapi.yaml. */
export interface ElectricityPrice {
  id: number
  provider: 'HVA_KOSTER_STROMMEN'
  priceArea: PriceArea
  startsAt: string
  endsAt: string
  pricePerKwh: number
  currency: 'NOK'
  fetchedAt: string
}

/** Response of `GET /electricity-prices`. */
export interface ElectricityPriceListResponse {
  priceArea: PriceArea
  provider: 'HVA_KOSTER_STROMMEN'
  currency: 'NOK'
  from: string
  to: string
  prices: ElectricityPrice[]
}

export interface ListElectricityPricesParams {
  priceArea: PriceArea
  /** Inclusive ISO start. */
  from: string
  /** Exclusive ISO end. */
  to: string
}
