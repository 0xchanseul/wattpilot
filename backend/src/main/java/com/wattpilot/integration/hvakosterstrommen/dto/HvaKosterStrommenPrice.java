package com.wattpilot.integration.hvakosterstrommen.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * One element of the Hva koster strømmen prices response
 * ({@code GET /api/v1/prices/{year}/{MM}-{dd}_{area}.json}), which is a bare JSON array.
 *
 * <p>The JSON names are the provider's; the Java names follow project conventions via
 * {@link JsonProperty}. {@code eurPerKwh} and {@code exchangeRate} are parsed but intentionally not
 * persisted — V1 stores the NOK price only and adds no columns for them.
 *
 * <p>Money is {@link BigDecimal} to avoid binary rounding error. {@code timeStart}/{@code timeEnd}
 * are {@link OffsetDateTime} so the {@code +01:00}/{@code +02:00} offset the provider sends (and
 * therefore the DST transition) is preserved.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HvaKosterStrommenPrice(
        @JsonProperty("NOK_per_kWh") BigDecimal nokPerKwh,
        @JsonProperty("EUR_per_kWh") BigDecimal eurPerKwh,
        @JsonProperty("EXR") BigDecimal exchangeRate,
        @JsonProperty("time_start") OffsetDateTime timeStart,
        @JsonProperty("time_end") OffsetDateTime timeEnd
) {
}
