package com.wattpilot.dashboard.dto;

import com.wattpilot.dashboard.repository.ChargingAggregateRow;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Baseline vs. realized cost over the last 30 days of {@code COMPLETED} sessions.
 *
 * <p>Despite the name, {@code optimizedCostNok} is backed by the realized {@code actualCostNok} sum,
 * not the plan's {@code optimized_cost_nok} snapshot — matching the charging-history convention that
 * only realized figures are summed (see {@link com.wattpilot.dashboard.repository.DashboardRepository}).
 * In V1 mock charging always finishes exactly as planned, so the two are numerically identical.
 */
public record CostComparison(
        BigDecimal baselineCostNok,
        BigDecimal optimizedCostNok,
        BigDecimal savingsNok,
        BigDecimal savingsPercent
) {

    private static final int PERCENT_SCALE = 2;

    public static CostComparison from(ChargingAggregateRow row) {
        BigDecimal baselineCostNok = row.totalBaselineCostNok() == null ? BigDecimal.ZERO : row.totalBaselineCostNok();
        BigDecimal optimizedCostNok = row.totalActualCostNok() == null ? BigDecimal.ZERO : row.totalActualCostNok();
        BigDecimal savingsNok = baselineCostNok.subtract(optimizedCostNok);

        return new CostComparison(baselineCostNok, optimizedCostNok, savingsNok,
                savingsPercent(savingsNok, baselineCostNok));
    }

    /** {@code savingsNok / baselineCostNok * 100}, zero when there is no baseline to compare against. */
    private static BigDecimal savingsPercent(BigDecimal savingsNok, BigDecimal baselineCostNok) {
        if (baselineCostNok.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(PERCENT_SCALE, RoundingMode.UNNECESSARY);
        }
        return savingsNok.multiply(BigDecimal.valueOf(100))
                .divide(baselineCostNok, PERCENT_SCALE, RoundingMode.HALF_UP);
    }
}
