package com.wattpilot.electricity.dto;

import com.wattpilot.common.PriceArea;
import com.wattpilot.electricity.entity.ElectricityPrice;
import com.wattpilot.electricity.entity.PriceProvider;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Public view of one hourly price. Matches the {@code ElectricityPrice} schema in docs/openapi.yaml.
 *
 * <p>Timestamps are rendered at the given zone (Europe/Oslo) so the offset in the response reflects
 * Norwegian wall-clock time, e.g. {@code 2026-08-24T01:00:00+02:00}.
 */
public record ElectricityPriceResponse(
        Long id,
        PriceProvider provider,
        PriceArea priceArea,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        BigDecimal pricePerKwh,
        String currency,
        OffsetDateTime fetchedAt
) {

    public static ElectricityPriceResponse from(ElectricityPrice price, ZoneId displayZone) {
        return new ElectricityPriceResponse(
                price.getId(),
                price.getProvider(),
                price.getPriceArea(),
                atZone(price.getStartsAt(), displayZone),
                atZone(price.getEndsAt(), displayZone),
                price.getPricePerKwh(),
                price.getCurrency(),
                atZone(price.getFetchedAt(), displayZone));
    }

    private static OffsetDateTime atZone(OffsetDateTime value, ZoneId displayZone) {
        return value.atZoneSameInstant(displayZone).toOffsetDateTime();
    }
}
