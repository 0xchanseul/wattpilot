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
 * Input for {@code POST /charging-schedules}: the original charging conditions plus the start and end
 * of the candidate the user picked from a preview.
 *
 * <p>The server never trusts a client-supplied cost, energy or slot list. It re-runs the calculation
 * from these conditions, finds the candidate whose window equals {@code selectedStartAt} /
 * {@code selectedEndAt}, and persists that one with server-computed figures.
 */
public record CreateChargingScheduleRequest(
        @NotNull @Positive Long evId,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
        BigDecimal currentBatteryPercent,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("100") @Digits(integer = 3, fraction = 2)
        BigDecimal targetBatteryPercent,
        @NotNull OffsetDateTime requiredCompletionAt,
        @NotNull PriceArea priceArea,
        @NotNull OffsetDateTime selectedStartAt,
        @NotNull OffsetDateTime selectedEndAt
) {
}
