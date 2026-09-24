package com.wattpilot.savings.repository;

import java.math.BigDecimal;

/**
 * Realized totals over a set of {@code COMPLETED} charging sessions, from {@link SavingsRepository}.
 *
 * <p>{@code totalBaselineCostNok} and {@code totalActualCostNok} are kept separate (rather than
 * pre-differenced) so {@code SavingsSummary} can derive both a savings amount and a savings rate
 * without a second query; both are {@code null} when {@code completedSessions} is zero.
 */
public record SavingsAggregateRow(
        Long completedSessions,
        BigDecimal totalEnergyKwh,
        BigDecimal totalBaselineCostNok,
        BigDecimal totalActualCostNok
) {
}
