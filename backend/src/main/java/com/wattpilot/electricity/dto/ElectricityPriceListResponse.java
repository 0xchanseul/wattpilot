package com.wattpilot.electricity.dto;

import com.wattpilot.common.PriceArea;
import com.wattpilot.electricity.entity.PriceProvider;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Response for {@code GET /electricity-prices}. Matches the {@code ElectricityPriceListResponse}
 * schema in docs/openapi.yaml: the query echo ({@code priceArea}, {@code provider}, {@code currency},
 * {@code from}, {@code to}) plus the hours themselves, ordered by start time.
 */
public record ElectricityPriceListResponse(
        PriceArea priceArea,
        PriceProvider provider,
        String currency,
        OffsetDateTime from,
        OffsetDateTime to,
        List<ElectricityPriceResponse> prices
) {

    public static ElectricityPriceListResponse of(PriceArea priceArea, PriceProvider provider, String currency,
                                                  OffsetDateTime from, OffsetDateTime to,
                                                  List<ElectricityPriceResponse> prices) {
        return new ElectricityPriceListResponse(priceArea, provider, currency, from, to, prices);
    }
}
