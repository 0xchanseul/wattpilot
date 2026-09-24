package com.wattpilot.savings.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One bucket's realized savings for the trend/bar chart — a calendar day, or the first day of a
 * calendar month when {@code granularity=MONTHLY} — both resolved in Europe/Oslo. Zero-filled by
 * {@code SavingsService} for a bucket with no completed session. Mirrors the {@code DailySavings}
 * schema in docs/openapi.yaml.
 */
public record DailySavings(
        LocalDate date,
        String currency,
        int sessionCount,
        BigDecimal energyKwh,
        BigDecimal optimizedCostNok,
        BigDecimal baselineCostNok,
        BigDecimal savingsNok
) {

    private static final String CURRENCY_NOK = "NOK";

    public static DailySavings zero(LocalDate date) {
        return new DailySavings(date, CURRENCY_NOK, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
