package com.wattpilot.dashboard.dto;

import com.wattpilot.charging.entity.ChargingPlan;
import com.wattpilot.charging.entity.ChargingSchedule;
import com.wattpilot.charging.entity.ChargingScheduleStatus;
import com.wattpilot.electricity.service.ElectricityPriceService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * The user's nearest active reservation: an {@code IN_PROGRESS} schedule if one is currently charging,
 * otherwise the earliest-starting {@code WAITING} one. {@code null} in {@link DashboardResponse} when
 * there is none.
 *
 * <p>{@code estimatedEnergyKwh}/{@code estimatedCostNok} come from the schedule (its snapshot at
 * confirmation time); {@code estimatedSavingsNok} is the plan's own {@code expectedSavingsNok}
 * (baseline - optimized) — legitimately an estimate here, since the charge has not happened yet.
 */
public record NextCharging(
        Long scheduleId,
        Long evId,
        String evName,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        BigDecimal estimatedEnergyKwh,
        BigDecimal estimatedCostNok,
        BigDecimal estimatedSavingsNok,
        ChargingScheduleStatus status
) {

    private static final ZoneId DISPLAY_ZONE = ElectricityPriceService.PRICE_ZONE;

    public static NextCharging of(ChargingSchedule schedule, ChargingPlan plan) {
        return new NextCharging(
                schedule.getId(),
                plan.getEvId(),
                plan.getEvName(),
                schedule.getScheduledStartAt().atZoneSameInstant(DISPLAY_ZONE).toOffsetDateTime(),
                schedule.getScheduledEndAt().atZoneSameInstant(DISPLAY_ZONE).toOffsetDateTime(),
                schedule.getExpectedEnergyKwh(),
                schedule.getEstimatedCostNok(),
                plan.getExpectedSavingsNok(),
                schedule.getStatus());
    }
}
