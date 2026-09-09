package com.wattpilot.history.repository;

import com.wattpilot.charging.entity.ChargingFailureCode;
import com.wattpilot.charging.entity.ChargingSessionStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Flat projection of one executed charging session joined to its schedule and plan, populated
 * directly by {@link ChargingHistoryRepository}. The list needs no entity hydration and no follow-up
 * lookups.
 *
 * <p>All cost fields are the plan snapshot carried onto the session at completion; they are non-null
 * only for a {@link ChargingSessionStatus#COMPLETED} session. Timestamps are the raw UTC instants
 * stored in the database; {@link com.wattpilot.history.dto.ChargingHistoryItem} converts them to the
 * display zone and derives the two savings figures.
 */
public record ChargingHistoryRow(
        Long sessionId,
        Long scheduleId,
        Long evId,
        String evName,
        ChargingSessionStatus status,
        OffsetDateTime recordedAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        BigDecimal actualEnergyKwh,
        BigDecimal actualCostNok,
        BigDecimal baselineCostNok,
        BigDecimal optimizedCostNok,
        ChargingFailureCode failureCode,
        String failureReason
) {
}
