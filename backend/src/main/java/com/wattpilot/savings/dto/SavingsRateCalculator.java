package com.wattpilot.savings.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Shared by every savings DTO with a rate field, so {@code DailySavings} and {@code SavingsPatternPoint} agree on scale and rounding. */
final class SavingsRateCalculator {

    private static final int PERCENT_SCALE = 2;

    private SavingsRateCalculator() {
    }

    /** {@code savingsNok / baselineCostNok * 100}, zero when there is no baseline to compare against. */
    static BigDecimal percent(BigDecimal savingsNok, BigDecimal baselineCostNok) {
        if (baselineCostNok.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(PERCENT_SCALE, RoundingMode.UNNECESSARY);
        }
        return savingsNok.multiply(BigDecimal.valueOf(100))
                .divide(baselineCostNok, PERCENT_SCALE, RoundingMode.HALF_UP);
    }
}
