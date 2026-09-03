package com.wattpilot.charging.dto;

import com.wattpilot.common.PriceArea;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Internal input to {@link com.wattpilot.charging.service.ChargingOptimizationService}. Mirrors the
 * {@code CreateChargingPlanRequest} schema in docs/openapi.yaml so a future controller can map an
 * HTTP request onto it directly.
 *
 * @param userId               caller, for the EV ownership check
 * @param evId                  EV to charge; must be owned by {@code userId} and ACTIVE
 * @param currentBatteryPercent present charge level, 0-100
 * @param targetBatteryPercent  desired charge level, greater than {@code currentBatteryPercent}, up to 100
 * @param earliestStartAt       earliest the charging may begin; {@code null} and past values default to now
 * @param requiredCompletionAt  charging must finish by this instant
 * @param priceArea             bidding zone whose stored prices are used
 */
public record OptimizationCommand(
        Long userId,
        Long evId,
        BigDecimal currentBatteryPercent,
        BigDecimal targetBatteryPercent,
        OffsetDateTime earliestStartAt,
        OffsetDateTime requiredCompletionAt,
        PriceArea priceArea
) {
}
