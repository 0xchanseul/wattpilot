package com.wattpilot.electricity.provider;

import com.wattpilot.common.PriceArea;
import com.wattpilot.electricity.dto.PriceSlot;

import java.time.LocalDate;
import java.util.List;

/**
 * Port for an external hourly-price source. V1 has a single implementation backed by Hva koster
 * strømmen; a future Tibber implementation plugs in here without touching the collection or
 * optimization logic.
 *
 * <p>Implementations translate the provider's response into {@link PriceSlot}s so callers never see
 * an external DTO.
 */
public interface ElectricityPriceProvider {

    /**
     * Fetches every hourly price the provider publishes for one Norwegian bidding zone on one
     * Norwegian calendar day. The number of slots is not assumed to be 24: a DST transition day has
     * 23 or 25.
     *
     * @throws PricesNotYetPublishedException when the provider has no data for the date yet
     * @throws ElectricityPriceProviderException on any other failure (HTTP error, timeout, unparseable body)
     */
    List<PriceSlot> fetchDailyPrices(PriceArea priceArea, LocalDate date);
}
