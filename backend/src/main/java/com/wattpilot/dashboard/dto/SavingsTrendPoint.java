package com.wattpilot.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One day's realized savings ({@code baselineCostNok - actualCostNok}) over {@code COMPLETED} sessions
 * for the Savings Trend chart. Every day in the requested 30-day window is present, zero-filled where
 * no session completed, in ascending date order.
 */
public record SavingsTrendPoint(
        LocalDate date,
        BigDecimal savingsNok
) {
}
