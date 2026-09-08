package com.wattpilot.history.repository;

import java.math.BigDecimal;

/**
 * Aggregate over a user's (optionally EV-scoped) charging history, from
 * {@link ChargingHistoryRepository}. Covers every {@code COMPLETED} + {@code FAILED} session
 * regardless of the list's status filter or pagination.
 *
 * <p>{@code totalEnergyKwh} and {@code totalRealizedSavingsNok} sum over the {@code COMPLETED} rows
 * only (a {@code FAILED} row's actual figures are null and {@code SUM} skips them); both are null when
 * there are no completed sessions.
 */
public record ChargingHistorySummaryRow(
        Long totalSessions,
        Long completedSessions,
        BigDecimal totalEnergyKwh,
        BigDecimal totalRealizedSavingsNok
) {
}
