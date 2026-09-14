package com.wattpilot.dashboard.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The price for the current hour against today's Norwegian-calendar-day average, in the user's
 * {@code defaultPriceArea}. {@code null} in {@link DashboardResponse} when no stored interval covers
 * "now" (e.g. today's prices have not been imported yet), so the rest of the Dashboard still loads.
 *
 * <p>{@code todayAveragePriceNokPerKwh} and {@code differencePercent} are independently nullable: an
 * average can be missing even when a current price exists (no hours stored yet today other than the
 * current one is impossible in practice, but a defensive null is cheaper than a second failure mode).
 */
public record CurrentElectricityPrice(
        BigDecimal priceNokPerKwh,
        BigDecimal todayAveragePriceNokPerKwh,
        BigDecimal differencePercent
) {

    private static final int PERCENT_SCALE = 2;

    public static CurrentElectricityPrice of(BigDecimal priceNokPerKwh, BigDecimal todayAveragePriceNokPerKwh) {
        return new CurrentElectricityPrice(
                priceNokPerKwh,
                todayAveragePriceNokPerKwh,
                differencePercent(priceNokPerKwh, todayAveragePriceNokPerKwh));
    }

    /** {@code (current - average) / average * 100}, or null if the average is missing or zero. */
    private static BigDecimal differencePercent(BigDecimal current, BigDecimal average) {
        if (average == null || average.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(average)
                .multiply(BigDecimal.valueOf(100))
                .divide(average, PERCENT_SCALE, RoundingMode.HALF_UP);
    }
}
