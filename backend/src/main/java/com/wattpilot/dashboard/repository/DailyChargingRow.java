package com.wattpilot.dashboard.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * One {@code COMPLETED} charging session's realized cost figures, from {@link DashboardRepository}.
 *
 * <p>Used only to build the Savings Trend chart: the raw UTC {@code completedAt} instants are grouped
 * into Europe/Oslo calendar days in {@code DashboardService}, since a timezone-aware {@code GROUP BY
 * date} is not expressible in portable JPQL.
 */
public record DailyChargingRow(
        OffsetDateTime completedAt,
        BigDecimal baselineCostNok,
        BigDecimal actualCostNok
) {
}
