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
 * Input for {@code POST /charging-plans/preview}: the charging conditions, no window pick yet.
 *
 * <p>Bean Validation covers per-field format and range only. The cross-field and temporal rules
 * ({@code targetBatteryPercent > currentBatteryPercent}, {@code requiredCompletionAt} in the future)
 * are business rules checked in the service against the injected {@code Clock}. The window always
 * starts at "now": there is no {@code earliestStartAt} in this flow.
 */
public record CreateChargingPlanPreviewRequest(
        @NotNull @Positive Long evId,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
        BigDecimal currentBatteryPercent,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("100") @Digits(integer = 3, fraction = 2)
        BigDecimal targetBatteryPercent,
        @NotNull OffsetDateTime requiredCompletionAt,
        @NotNull PriceArea priceArea
) {
}
