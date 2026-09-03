package com.wattpilot.electricity.dto;

import com.wattpilot.electricity.entity.ElectricityPrice;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Minimal read projection of one stored hour: just the fields charging optimization needs.
 *
 * <p>Timestamps are rendered at the given zone (Europe/Oslo) so a recommended charging window carries
 * Norwegian wall-clock offsets, consistent with every other price-derived response.
 */
public record PricePoint(
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        BigDecimal pricePerKwh
) {

    public static PricePoint from(ElectricityPrice price, ZoneId displayZone) {
        return new PricePoint(
                price.getStartsAt().atZoneSameInstant(displayZone).toOffsetDateTime(),
                price.getEndsAt().atZoneSameInstant(displayZone).toOffsetDateTime(),
                price.getPricePerKwh());
    }
}
