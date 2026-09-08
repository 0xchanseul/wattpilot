package com.wattpilot.charging.dto;

import com.wattpilot.charging.entity.ChargingPlan;
import com.wattpilot.charging.entity.ChargingSchedule;
import com.wattpilot.charging.entity.ChargingScheduleStatus;
import com.wattpilot.charging.entity.ChargingSession;
import com.wattpilot.electricity.service.ElectricityPriceService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * A slim view of a just-finished charging schedule for the Schedules overview's {@code recentActivity}
 * block. This is deliberately not the full {@link ChargingScheduleResponse}: the overview only needs to
 * show "the last few things that happened", and the full result picture (costs, savings, slots,
 * per-hour breakdown) belongs to the charging-history endpoints.
 *
 * <p>{@code status} is {@code COMPLETED} or {@code FAILED}. {@code evName} is the snapshot stored on the
 * plan at confirmation time. Timestamps are rendered in Europe/Oslo.
 */
public record ChargingScheduleRecentActivity(
        Long scheduleId,
        Long sessionId,
        Long evId,
        String evName,
        ChargingScheduleStatus status,
        OffsetDateTime scheduledStartAt,
        OffsetDateTime scheduledEndAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        BigDecimal actualEnergyKwh
) {

    private static final ZoneId DISPLAY_ZONE = ElectricityPriceService.PRICE_ZONE;

    public static ChargingScheduleRecentActivity of(ChargingSchedule schedule, ChargingPlan plan,
                                                    ChargingSession session) {
        return new ChargingScheduleRecentActivity(
                schedule.getId(),
                session == null ? null : session.getId(),
                plan.getEvId(),
                plan.getEvName(),
                schedule.getStatus(),
                atDisplayZone(schedule.getScheduledStartAt()),
                atDisplayZone(schedule.getScheduledEndAt()),
                session == null ? null : atDisplayZone(session.getStartedAt()),
                session == null ? null : atDisplayZone(session.getCompletedAt()),
                session == null ? null : session.getActualEnergyKwh());
    }

    private static OffsetDateTime atDisplayZone(OffsetDateTime value) {
        return value == null ? null : value.atZoneSameInstant(DISPLAY_ZONE).toOffsetDateTime();
    }
}
