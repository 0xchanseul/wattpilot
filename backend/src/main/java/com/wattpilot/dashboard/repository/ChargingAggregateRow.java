package com.wattpilot.dashboard.repository;

import java.math.BigDecimal;

/**
 * Realized totals over a set of {@code COMPLETED} charging sessions, from {@link DashboardRepository}.
 *
 * <p>{@code totalBaselineCostNok} and {@code totalActualCostNok} are kept separate (rather than
 * pre-differenced) so callers can derive both a savings amount and a savings percentage without a
 * second query; both are {@code null} when {@code completedSessions} is zero.
 */
public record ChargingAggregateRow(
        Long completedSessions,
        BigDecimal totalEnergyKwh,
        BigDecimal totalBaselineCostNok,
        BigDecimal totalActualCostNok
) {
}
