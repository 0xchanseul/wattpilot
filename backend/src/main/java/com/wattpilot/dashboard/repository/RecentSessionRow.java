package com.wattpilot.dashboard.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * One recently completed charging session for the Dashboard's Recent Charging Sessions block, from
 * {@link DashboardRepository}. A slim projection compared to
 * {@link com.wattpilot.history.repository.ChargingHistoryRow}: only the fields the Dashboard shows,
 * plus {@code baselineCostNok} so {@code realizedSavingsNok} can be derived without a second lookup.
 */
public record RecentSessionRow(
        Long sessionId,
        Long evId,
        String evName,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        BigDecimal actualEnergyKwh,
        BigDecimal actualCostNok,
        BigDecimal baselineCostNok
) {
}
