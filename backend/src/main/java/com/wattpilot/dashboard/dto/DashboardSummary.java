package com.wattpilot.dashboard.dto;

import com.wattpilot.dashboard.repository.ChargingAggregateRow;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * All-time realized totals over every {@code COMPLETED} charging session for the user.
 *
 * <p>{@code totalSavingsNok} is {@code baselineCostNok - actualCostNok} (realized), never the plan's
 * own {@code estimatedSavingsNok} — see {@link com.wattpilot.dashboard.repository.DashboardRepository}.
 * {@code averageCostPerKwh} is {@code totalActualCostNok / totalEnergyKwh}, not a simple average of
 * per-session unit prices, so it is weighted by how much was actually charged.
 */
public record DashboardSummary(
        BigDecimal totalSavingsNok,
        BigDecimal totalEnergyKwh,
        long totalSessions,
        BigDecimal averageCostPerKwh
) {

    private static final int COST_PER_KWH_SCALE = 4;

    public static DashboardSummary from(ChargingAggregateRow row) {
        long completedSessions = row.completedSessions() == null ? 0L : row.completedSessions();
        BigDecimal totalEnergyKwh = row.totalEnergyKwh() == null ? BigDecimal.ZERO : row.totalEnergyKwh();
        BigDecimal totalActualCostNok = row.totalActualCostNok() == null ? BigDecimal.ZERO : row.totalActualCostNok();
        BigDecimal totalBaselineCostNok = row.totalBaselineCostNok() == null ? BigDecimal.ZERO : row.totalBaselineCostNok();

        return new DashboardSummary(
                totalBaselineCostNok.subtract(totalActualCostNok),
                totalEnergyKwh,
                completedSessions,
                averageCostPerKwh(totalActualCostNok, totalEnergyKwh));
    }

    /** {@code totalActualCostNok / totalEnergyKwh}, or zero when nothing has been charged yet. */
    private static BigDecimal averageCostPerKwh(BigDecimal totalActualCostNok, BigDecimal totalEnergyKwh) {
        if (totalEnergyKwh.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(COST_PER_KWH_SCALE, RoundingMode.UNNECESSARY);
        }
        return totalActualCostNok.divide(totalEnergyKwh, COST_PER_KWH_SCALE, RoundingMode.HALF_UP);
    }
}
