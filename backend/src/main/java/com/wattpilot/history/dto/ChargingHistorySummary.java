package com.wattpilot.history.dto;

import com.wattpilot.history.repository.ChargingHistorySummaryRow;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Header totals for the charging-history list. Matches the {@code ChargingHistorySummary} schema in
 * docs/openapi.yaml.
 *
 * <p>Covers every {@code COMPLETED} + {@code FAILED} session in scope (optionally narrowed by
 * {@code evId}), regardless of the list's {@code status} filter or pagination. {@code totalEnergyKwh}
 * and {@code totalSavingsNok} are realized figures — summed over the {@code COMPLETED} sessions only,
 * using {@code baselineCostNok - actualCostNok} for savings (never the estimate).
 *
 * <p>The aggregation itself lives in {@link com.wattpilot.history.repository.ChargingHistoryRepository}
 * so a later dashboard / savings-summary feature can reuse it.
 */
public record ChargingHistorySummary(
        long totalSessions,
        BigDecimal successRate,
        BigDecimal totalEnergyKwh,
        BigDecimal totalSavingsNok
) {

    public static ChargingHistorySummary from(ChargingHistorySummaryRow row) {
        long total = row.totalSessions() == null ? 0L : row.totalSessions();
        long completed = row.completedSessions() == null ? 0L : row.completedSessions();
        return new ChargingHistorySummary(
                total,
                successRate(total, completed),
                row.totalEnergyKwh() == null ? BigDecimal.ZERO : row.totalEnergyKwh(),
                row.totalRealizedSavingsNok() == null ? BigDecimal.ZERO : row.totalRealizedSavingsNok());
    }

    /** COMPLETED as a percentage of all history sessions, one decimal place. Zero when there are none. */
    private static BigDecimal successRate(long total, long completed) {
        if (total == 0L) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.UNNECESSARY);
        }
        return BigDecimal.valueOf(completed)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }
}
