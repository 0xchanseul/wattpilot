package com.wattpilot.history.dto;

import com.wattpilot.charging.dto.ChargingPlanSlot;
import com.wattpilot.charging.dto.EvSnapshot;
import com.wattpilot.charging.entity.ChargingFailureCode;
import com.wattpilot.charging.entity.ChargingPlan;
import com.wattpilot.charging.entity.ChargingSchedule;
import com.wattpilot.charging.entity.ChargingSession;
import com.wattpilot.charging.entity.ChargingSessionStatus;
import com.wattpilot.common.PriceArea;
import com.wattpilot.electricity.service.ElectricityPriceService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * The "charging receipt" for one executed session — the drill-down behind a {@link ChargingHistoryItem}.
 * Matches the {@code ChargingHistoryDetail} schema in docs/openapi.yaml.
 *
 * <p>Three groups, deliberately separated so a plan estimate is never shown as a realized result:
 * <ul>
 *   <li><b>Conditions</b> — what the user asked for ({@code startBatteryPercent}, {@code targetBatteryPercent},
 *       {@code priceArea}, {@code earliestStartAt}, {@code requiredCompletionAt}) and the EV snapshot.</li>
 *   <li><b>Plan</b> — what the optimizer recommended: {@code recommendedStartAt}/{@code recommendedEndAt},
 *       {@code calculatedEnergyKwh} (battery-side), {@code plannedEnergyKwh} (grid-side),
 *       {@code optimizedCostNok}, {@code baselineCostNok}, {@code estimatedSavingsNok}
 *       ({@code baseline - optimized}), and the per-hour {@code plannedSlots}. Present for every history
 *       entry, including a {@code FAILED} one (the plan still succeeded — only the execution failed).</li>
 *   <li><b>Actual</b> — the realized outcome: {@code actualEnergyKwh}, {@code actualCostNok},
 *       {@code realizedSavingsNok} ({@code baseline - actual}). Non-null only for a
 *       {@link ChargingSessionStatus#COMPLETED} session; a {@code FAILED} one carries
 *       {@code failureCode}/{@code failureReason}.</li>
 * </ul>
 *
 * <p>{@code plannedSlots} come from {@code charging_plan_slots}: they are the plan's per-hour figures
 * ({@code plannedEnergyKwh}, {@code expectedCostNok}, {@code pricePerKwh}), never a re-measured
 * per-hour actual — V1 stores no per-slot execution result. Timestamps are rendered in Europe/Oslo.
 */
public record ChargingHistoryDetail(
        Long sessionId,
        Long scheduleId,
        Long planId,
        Long evId,
        EvSnapshot evSnapshot,
        BigDecimal startBatteryPercent,
        BigDecimal targetBatteryPercent,
        PriceArea priceArea,
        OffsetDateTime earliestStartAt,
        OffsetDateTime requiredCompletionAt,
        OffsetDateTime recommendedStartAt,
        OffsetDateTime recommendedEndAt,
        BigDecimal calculatedEnergyKwh,
        BigDecimal effectiveChargingPowerKw,
        Integer estimatedDurationMinutes,
        BigDecimal plannedEnergyKwh,
        BigDecimal optimizedCostNok,
        BigDecimal baselineCostNok,
        BigDecimal estimatedSavingsNok,
        ChargingSessionStatus status,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        BigDecimal actualEnergyKwh,
        BigDecimal actualCostNok,
        BigDecimal realizedSavingsNok,
        ChargingFailureCode failureCode,
        String failureReason,
        List<ChargingPlanSlot> plannedSlots
) {

    private static final ZoneId DISPLAY_ZONE = ElectricityPriceService.PRICE_ZONE;

    public static ChargingHistoryDetail of(ChargingSession session, ChargingSchedule schedule,
                                           ChargingPlan plan, List<ChargingPlanSlot> plannedSlots) {
        return new ChargingHistoryDetail(
                session.getId(),
                schedule.getId(),
                plan.getId(),
                plan.getEvId(),
                new EvSnapshot(
                        plan.getEvName(),
                        plan.getEvManufacturer(),
                        plan.getEvModel(),
                        plan.getBatteryCapacityKwh(),
                        plan.getMaxAcChargingPowerKw(),
                        plan.getDefaultChargerPowerKw()),
                plan.getCurrentBatteryPercent(),
                plan.getTargetBatteryPercent(),
                plan.getPriceArea(),
                atDisplayZone(plan.getEarliestStartAt()),
                atDisplayZone(plan.getRequiredCompletionAt()),
                atDisplayZone(plan.getRecommendedStartAt()),
                atDisplayZone(plan.getRecommendedEndAt()),
                plan.getCalculatedEnergyKwh(),
                plan.getEffectiveChargingPowerKw(),
                plan.getEstimatedDurationMinutes(),
                plan.getExpectedEnergyKwh(),
                plan.getEstimatedCostNok(),
                plan.getBaselineCostNok(),
                plan.getExpectedSavingsNok(),
                session.getStatus(),
                atDisplayZone(session.getStartedAt()),
                atDisplayZone(session.getCompletedAt()),
                session.getActualEnergyKwh(),
                session.getActualCostNok(),
                ChargingHistoryItem.difference(session.getBaselineCostNok(), session.getActualCostNok()),
                session.getFailureCode(),
                session.getFailureReason(),
                plannedSlots);
    }

    private static OffsetDateTime atDisplayZone(OffsetDateTime value) {
        return value == null ? null : value.atZoneSameInstant(DISPLAY_ZONE).toOffsetDateTime();
    }
}
