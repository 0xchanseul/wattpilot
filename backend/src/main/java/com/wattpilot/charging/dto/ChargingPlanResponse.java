package com.wattpilot.charging.dto;

import com.wattpilot.charging.entity.ChargingPlan;
import com.wattpilot.charging.entity.ChargingPlanStatus;
import com.wattpilot.common.PriceArea;
import com.wattpilot.electricity.service.ElectricityPriceService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Public view of a charging plan. Matches the {@code ChargingPlan} schema in docs/openapi.yaml and
 * deliberately omits the owning user id.
 *
 * <p>Only ever returned for a {@link ChargingPlanStatus#SUCCEEDED} plan: a FAILED attempt is
 * reported as a 422 with {@link ChargingPlanInfeasibleResponse}. Timestamps are rendered in
 * Europe/Oslo, consistent with every other price-derived response.
 */
public record ChargingPlanResponse(
        Long id,
        Long evId,
        EvSnapshot evSnapshot,
        ChargingPlanStatus status,
        BigDecimal currentBatteryPercent,
        BigDecimal targetBatteryPercent,
        OffsetDateTime requiredCompletionAt,
        OffsetDateTime earliestStartAt,
        PriceArea priceArea,
        BigDecimal calculatedEnergyKwh,
        BigDecimal effectiveChargingPowerKw,
        Integer estimatedDurationMinutes,
        OffsetDateTime recommendedStartAt,
        OffsetDateTime recommendedEndAt,
        BigDecimal expectedEnergyKwh,
        BigDecimal estimatedCostNok,
        BigDecimal baselineCostNok,
        BigDecimal expectedSavingsNok,
        List<ChargingPlanSlot> slots,
        OffsetDateTime createdAt
) {

    private static final ZoneId DISPLAY_ZONE = ElectricityPriceService.PRICE_ZONE;

    public static ChargingPlanResponse of(ChargingPlan plan, List<ChargingPlanSlot> slots) {
        return new ChargingPlanResponse(
                plan.getId(),
                plan.getEvId(),
                new EvSnapshot(
                        plan.getEvName(),
                        plan.getEvManufacturer(),
                        plan.getEvModel(),
                        plan.getBatteryCapacityKwh(),
                        plan.getMaxAcChargingPowerKw(),
                        plan.getDefaultChargerPowerKw()),
                plan.getStatus(),
                plan.getCurrentBatteryPercent(),
                plan.getTargetBatteryPercent(),
                atDisplayZone(plan.getRequiredCompletionAt()),
                atDisplayZone(plan.getEarliestStartAt()),
                plan.getPriceArea(),
                plan.getCalculatedEnergyKwh(),
                plan.getEffectiveChargingPowerKw(),
                plan.getEstimatedDurationMinutes(),
                atDisplayZone(plan.getRecommendedStartAt()),
                atDisplayZone(plan.getRecommendedEndAt()),
                plan.getExpectedEnergyKwh(),
                plan.getEstimatedCostNok(),
                plan.getBaselineCostNok(),
                plan.getExpectedSavingsNok(),
                slots,
                atDisplayZone(plan.getCreatedAt()));
    }

    private static OffsetDateTime atDisplayZone(OffsetDateTime value) {
        return value == null ? null : value.atZoneSameInstant(DISPLAY_ZONE).toOffsetDateTime();
    }
}
