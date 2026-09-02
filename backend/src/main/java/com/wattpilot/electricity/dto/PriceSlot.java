package com.wattpilot.electricity.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * One hour of price data handed to {@code ElectricityPriceService.importPrices}.
 *
 * <p>This is the internal import boundary. The Hva koster strømmen client (built in a later step)
 * maps each entry of its API response onto this record, so the persistence logic never depends on an
 * external response shape.
 *
 * @param startsAt    start of the hour, with the offset the provider reported
 * @param endsAt      end of the hour
 * @param pricePerKwh spot price for the hour, in {@code currency} per kWh
 * @param currency    ISO 4217 code; {@code null} is treated as {@code NOK}
 */
public record PriceSlot(
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        BigDecimal pricePerKwh,
        String currency
) {
}
