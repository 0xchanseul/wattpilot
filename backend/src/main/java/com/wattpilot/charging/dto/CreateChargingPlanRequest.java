package com.wattpilot.charging.dto;

import com.wattpilot.common.PriceArea;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Matches the {@code CreateChargingPlanRequest} schema in docs/openapi.yaml.
 *
 * <p>Bean Validation covers the per-field format and range only. The cross-field and temporal rules
 * ({@code targetBatteryPercent > currentBatteryPercent}, {@code requiredCompletionAt} in the future,
 * {@code earliestStartAt} before {@code requiredCompletionAt}) are business rules checked in the
 * service against the injected {@code Clock}.
 */
public record CreateChargingPlanRequest(
        @NotNull @Positive Long evId,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
        BigDecimal currentBatteryPercent,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("100") @Digits(integer = 3, fraction = 2)
        BigDecimal targetBatteryPercent,
        @NotNull OffsetDateTime requiredCompletionAt,
        OffsetDateTime earliestStartAt,
        @NotNull PriceArea priceArea
) {
}
