package com.wattpilot.savings.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * One bucket of {@code GET /savings/patterns}, zero-filled by {@code SavingsService} when no
 * charging plan slot fell in it. {@code bucket} means ISO-8601 day of week (1=Monday..7=Sunday) for
 * {@code groupBy=WEEKDAY}, or the starting hour of day (0-23) for {@code groupBy=HOUR_OF_DAY}.
 * Figures are summed across charging plan slots, not whole sessions — see
 * {@code SavingsService.getPatterns}. Mirrors the {@code SavingsPatternPoint} schema in
 * docs/openapi.yaml.
 */
public record SavingsPatternPoint(
        int bucket,
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
    private static final int COST_SCALE = 4;

    public static SavingsPatternPoint zero(int bucket) {
        BigDecimal zeroRate = BigDecimal.ZERO.setScale(PERCENT_SCALE, RoundingMode.UNNECESSARY);
        return new SavingsPatternPoint(bucket, CURRENCY_NOK, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, zeroRate);
    }

    /** {@code baselineCostNok} is rounded here: it is the sum of per-slot prorated shares, so it arrives at a wider, uncontrolled scale. */
    public static SavingsPatternPoint of(int bucket, int sessionCount, BigDecimal energyKwh,
                                         BigDecimal optimizedCostNok, BigDecimal baselineCostNok) {
        BigDecimal roundedBaselineCostNok = baselineCostNok.setScale(COST_SCALE, RoundingMode.HALF_UP);
        BigDecimal savingsNok = roundedBaselineCostNok.subtract(optimizedCostNok);
        return new SavingsPatternPoint(bucket, CURRENCY_NOK, sessionCount, energyKwh, optimizedCostNok,
                roundedBaselineCostNok, savingsNok, SavingsRateCalculator.percent(savingsNok, roundedBaselineCostNok));
    }
}
