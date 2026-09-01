package com.wattpilot.ev.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Matches the {@code CreateEvRequest} schema in docs/openapi.yaml.
 *
 * <p>The 22 kW ceiling on both charging-power fields is the practical limit of Norwegian
 * three-phase home AC charging and matches the {@code evs} CHECK constraints. Battery capacity has
 * no documented upper bound; {@code @Digits} only keeps a value from overflowing the NUMERIC(8,2)
 * column.
 */
public record CreateEvRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 100) String manufacturer,
        @NotBlank @Size(max = 100) String model,
        @NotNull @Positive @Digits(integer = 6, fraction = 2) BigDecimal batteryCapacityKwh,
        @NotNull @Positive @DecimalMax("22.00") @Digits(integer = 6, fraction = 2) BigDecimal maxAcChargingPowerKw,
        @NotNull @Positive @DecimalMax("22.00") @Digits(integer = 6, fraction = 2) BigDecimal defaultChargerPowerKw
) {
}
