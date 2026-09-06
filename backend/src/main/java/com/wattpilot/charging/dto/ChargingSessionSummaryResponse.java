package com.wattpilot.charging.dto;

import com.wattpilot.charging.entity.ChargingFailureCode;
import com.wattpilot.charging.entity.ChargingSession;
import com.wattpilot.charging.entity.ChargingSessionStatus;
import com.wattpilot.electricity.service.ElectricityPriceService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Execution outcome of one charging schedule's Mock Charging attempt, embedded in
 * {@link ChargingScheduleResponse}. Absent until the schedule's first execution attempt: a schedule
 * still {@code WAITING} has no session yet.
 */
public record ChargingSessionSummaryResponse(
        ChargingSessionStatus status,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        BigDecimal actualEnergyKwh,
        BigDecimal actualCostNok,
        BigDecimal baselineCostNok,
        BigDecimal optimizedCostNok,
        BigDecimal estimatedSavingsNok,
        ChargingFailureCode failureCode,
        String failureReason
) {

    private static final ZoneId DISPLAY_ZONE = ElectricityPriceService.PRICE_ZONE;

    public static ChargingSessionSummaryResponse of(ChargingSession session) {
        return new ChargingSessionSummaryResponse(
                session.getStatus(),
                atDisplayZone(session.getStartedAt()),
                atDisplayZone(session.getCompletedAt()),
                session.getActualEnergyKwh(),
                session.getActualCostNok(),
                session.getBaselineCostNok(),
                session.getOptimizedCostNok(),
                session.getEstimatedSavingsNok(),
                session.getFailureCode(),
                session.getFailureReason());
    }

    private static OffsetDateTime atDisplayZone(OffsetDateTime value) {
        return value == null ? null : value.atZoneSameInstant(DISPLAY_ZONE).toOffsetDateTime();
    }
}
