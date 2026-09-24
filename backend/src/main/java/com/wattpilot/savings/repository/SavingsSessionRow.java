package com.wattpilot.savings.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * One {@code COMPLETED} charging session's realized figures, from {@link SavingsRepository}.
 *
 * <p>Used to build the {@code GET /savings/daily} trend: the raw UTC {@code completedAt} instants
 * are grouped into Europe/Oslo calendar days or months in {@code SavingsService}, since a
 * timezone-aware {@code GROUP BY} is not expressible in portable JPQL.
 */
public record SavingsSessionRow(
        OffsetDateTime completedAt,
        BigDecimal actualEnergyKwh,
        BigDecimal baselineCostNok,
        BigDecimal actualCostNok
) {
}
