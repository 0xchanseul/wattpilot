package com.wattpilot.savings.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
        BigDecimal savingsNok,
        BigDecimal savingsRatePercent
) {

    private static final String CURRENCY_NOK = "NOK";
    private static final int PERCENT_SCALE = 2;

    public static DailySavings zero(LocalDate date) {
        BigDecimal zeroRate = BigDecimal.ZERO.setScale(PERCENT_SCALE, RoundingMode.UNNECESSARY);
        return new DailySavings(date, CURRENCY_NOK, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, zeroRate);
    }

    public static DailySavings of(LocalDate date, int sessionCount, BigDecimal energyKwh,
                                  BigDecimal optimizedCostNok, BigDecimal baselineCostNok) {
        BigDecimal savingsNok = baselineCostNok.subtract(optimizedCostNok);
        return new DailySavings(date, CURRENCY_NOK, sessionCount, energyKwh, optimizedCostNok, baselineCostNok,
                savingsNok, SavingsRateCalculator.percent(savingsNok, baselineCostNok));
    }
}
