package com.wattpilot.charging.dto;

import com.wattpilot.common.PriceArea;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Response for {@code POST /charging-plans/preview}. Nothing here is persisted.
 *
 * <p>{@code calculatedEnergyKwh}, {@code effectiveChargingPowerKw} and {@code estimatedDurationMinutes}
 * are shared by every candidate; each {@link ChargingCandidate} then carries its own window, cost and
 * savings. {@code candidates} is ordered cheapest first and holds at most three entries.
 */
public record ChargingPlanPreviewResponse(
        Long evId,
        BigDecimal currentBatteryPercent,
        BigDecimal targetBatteryPercent,
        OffsetDateTime requiredCompletionAt,
        PriceArea priceArea,
        BigDecimal calculatedEnergyKwh,
        BigDecimal effectiveChargingPowerKw,
        int estimatedDurationMinutes,
        List<ChargingCandidate> candidates
) {
}
