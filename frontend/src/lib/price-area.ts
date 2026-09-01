import type { PriceArea } from '@/types/api'

/**
 * Norwegian electricity price areas (bidding zones NO1-NO5), with a human-readable region name
 * so the raw code is never the only thing shown to the user.
 */
export const PRICE_AREAS = ['NO1', 'NO2', 'NO3', 'NO4', 'NO5'] as const satisfies readonly PriceArea[]

const PRICE_AREA_REGIONS: Record<PriceArea, string> = {
  NO1: 'Oslo · Eastern Norway',
  NO2: 'Kristiansand · Southern Norway',
  NO3: 'Trondheim · Central Norway',
  NO4: 'Tromsø · Northern Norway',
  NO5: 'Bergen · Western Norway',
}

/** e.g. "Oslo · Eastern Norway (NO1)" */
export function priceAreaLabel(area: PriceArea): string {
  return `${PRICE_AREA_REGIONS[area]} (${area})`
}
