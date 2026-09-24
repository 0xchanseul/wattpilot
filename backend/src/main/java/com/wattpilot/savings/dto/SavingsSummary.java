package com.wattpilot.savings.dto;

import com.wattpilot.savings.repository.SavingsAggregateRow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Realized totals over {@code COMPLETED} charging sessions in {@code [from, to]} (both inclusive,
 * Europe/Oslo calendar days), optionally scoped to one EV. Mirrors the {@code SavingsSummary}
 * schema in docs/openapi.yaml.
 *
 * <p>{@code optimizedCostNok} is named after the plan's field but is backed by the realized
 * {@code actualCostNok} sum, matching the Dashboard/History convention that only realized figures
 * are aggregated — never {@code charging_sessions.estimated_savings_nok}.
 */
public record SavingsSummary(
        LocalDate from,
        LocalDate to,
        Long evId,
        String currency,
        long completedSessionCount,
        BigDecimal totalEnergyKwh,
        BigDecimal optimizedCostNok,
        BigDecimal baselineCostNok,
        BigDecimal totalSavingsNok,
        BigDecimal savingsRatePercent
) {

    private static final String CURRENCY_NOK = "NOK";
    private static final int PERCENT_SCALE = 2;

    public static SavingsSummary of(LocalDate from, LocalDate to, Long evId, SavingsAggregateRow row) {
        long completedSessions = row.completedSessions() == null ? 0L : row.completedSessions();
        BigDecimal totalEnergyKwh = row.totalEnergyKwh() == null ? BigDecimal.ZERO : row.totalEnergyKwh();
        BigDecimal optimizedCostNok = row.totalActualCostNok() == null ? BigDecimal.ZERO : row.totalActualCostNok();
        BigDecimal baselineCostNok = row.totalBaselineCostNok() == null ? BigDecimal.ZERO : row.totalBaselineCostNok();
        BigDecimal totalSavingsNok = baselineCostNok.subtract(optimizedCostNok);

        return new SavingsSummary(from, to, evId, CURRENCY_NOK, completedSessions, totalEnergyKwh,
                optimizedCostNok, baselineCostNok, totalSavingsNok,
                savingsRatePercent(totalSavingsNok, baselineCostNok));
    }

    /** {@code totalSavingsNok / baselineCostNok * 100}, zero when there is no baseline to compare against. */
    private static BigDecimal savingsRatePercent(BigDecimal savingsNok, BigDecimal baselineCostNok) {
        if (baselineCostNok.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(PERCENT_SCALE, RoundingMode.UNNECESSARY);
        }
        return savingsNok.multiply(BigDecimal.valueOf(100))
                .divide(baselineCostNok, PERCENT_SCALE, RoundingMode.HALF_UP);
    }
}
