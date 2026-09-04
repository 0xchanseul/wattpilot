package com.wattpilot.charging.dto;

import com.wattpilot.charging.entity.ChargingPlan;
import com.wattpilot.charging.entity.ChargingSchedule;
import com.wattpilot.charging.entity.ChargingScheduleStatus;
import com.wattpilot.electricity.service.ElectricityPriceService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Response for the charging-schedule endpoints. Carries the schedule plus the plan-level cost picture
 * ({@code baselineCostNok}, {@code expectedSavingsNok}) so the confirmation screen needs no second
 * call; {@code planId} lets the client fetch the full plan if it wants the EV snapshot.
 *
 * <p>Timestamps are rendered in Europe/Oslo, consistent with every other price-derived response.
 */
public record ChargingScheduleResponse(
        Long id,
        Long planId,
        Long evId,
        ChargingScheduleStatus status,
        OffsetDateTime scheduledStartAt,
        OffsetDateTime scheduledEndAt,
        BigDecimal calculatedEnergyKwh,
        BigDecimal expectedEnergyKwh,
        BigDecimal estimatedCostNok,
        BigDecimal baselineCostNok,
        BigDecimal expectedSavingsNok,
        List<ChargingPlanSlot> slots,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    private static final ZoneId DISPLAY_ZONE = ElectricityPriceService.PRICE_ZONE;

    public static ChargingScheduleResponse of(ChargingSchedule schedule, ChargingPlan plan,
                                              List<ChargingPlanSlot> slots) {
        return new ChargingScheduleResponse(
                schedule.getId(),
                plan.getId(),
                plan.getEvId(),
                schedule.getStatus(),
                atDisplayZone(schedule.getScheduledStartAt()),
                atDisplayZone(schedule.getScheduledEndAt()),
                plan.getCalculatedEnergyKwh(),
                schedule.getExpectedEnergyKwh(),
                schedule.getEstimatedCostNok(),
                plan.getBaselineCostNok(),
                plan.getExpectedSavingsNok(),
                slots,
                atDisplayZone(schedule.getCreatedAt()),
                atDisplayZone(schedule.getUpdatedAt()));
    }

    private static OffsetDateTime atDisplayZone(OffsetDateTime value) {
        return value == null ? null : value.atZoneSameInstant(DISPLAY_ZONE).toOffsetDateTime();
    }
}
