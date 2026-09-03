package com.wattpilot.charging.service;

import com.wattpilot.charging.dto.ChargingPlanSlot;
import com.wattpilot.charging.dto.EvSnapshot;
import com.wattpilot.charging.dto.OptimizationResult;
import com.wattpilot.electricity.dto.PricePoint;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChargingWindowCalculatorTest {

    private static final BigDecimal EFFICIENCY = new BigDecimal("0.9");

    private final ChargingWindowCalculator calculator = new ChargingWindowCalculator();

    @Test
    void picksTheCheapestContinuousWindowWithAPartialTrailingSlot() {
        // delivered power 10 kW, effective 9 kW, 22.5 kWh -> 150 minutes.
        OptimizationResult result = calculator.optimize(
                ev("11", "10"),
                new BigDecimal("22.5"),
                EFFICIENCY,
                at("18:20"),
                at("23:00"),
                hourly("18:00", "1.20", "0.80", "0.50", "0.40", "0.90"));

        OptimizationResult.Success success = success(result);
        assertThat(success.estimatedDurationMinutes()).isEqualTo(150);
        assertThat(success.effectiveChargingPowerKw()).isEqualByComparingTo("10.00");
        assertThat(success.calculatedEnergyKwh()).isEqualByComparingTo("22.50");
        assertThat(success.recommendedStartAt()).isEqualTo(at("20:00"));
        assertThat(success.recommendedEndAt()).isEqualTo(at("22:30"));
        assertThat(success.estimatedCostNok()).isEqualByComparingTo("13.5000");
        assertThat(success.expectedEnergyKwh()).isEqualByComparingTo("25.00");
        assertThat(success.baselineCostNok()).isEqualByComparingTo("20.1667");
        assertThat(success.expectedSavingsNok()).isEqualByComparingTo("6.6667");

        assertThat(success.slots()).hasSize(3);
        assertThat(success.slots().get(0).startsAt()).isEqualTo(at("20:00"));
        assertThat(success.slots().get(0).plannedEnergyKwh()).isEqualByComparingTo("10.00");
        assertThat(success.slots().get(2).startsAt()).isEqualTo(at("22:00"));
        assertThat(success.slots().get(2).endsAt()).isEqualTo(at("22:30"));
        assertThat(success.slots().get(2).plannedEnergyKwh()).isEqualByComparingTo("5.00");
        assertThat(success.slots().get(2).expectedCostNok()).isEqualByComparingTo("4.5000");
    }

    @Test
    void withFlatPricesChoosesTheEarliestWindowAndReportsZeroSavings() {
        OptimizationResult result = calculator.optimize(
                ev("10", "10"),
                new BigDecimal("22.5"),
                EFFICIENCY,
                at("18:00"),
                at("23:00"),
                hourly("18:00", "0.50", "0.50", "0.50", "0.50", "0.50"));

        OptimizationResult.Success success = success(result);
        assertThat(success.recommendedStartAt()).isEqualTo(at("18:00"));
        assertThat(success.estimatedCostNok()).isEqualByComparingTo("12.5000");
        assertThat(success.baselineCostNok()).isEqualByComparingTo("12.5000");
        assertThat(success.expectedSavingsNok()).isEqualByComparingTo("0.0000");
    }

    @Test
    void whenEarlyHoursAreExpensiveItStartsAsLateAsTheDeadlineAllows() {
        OptimizationResult result = calculator.optimize(
                ev("10", "10"),
                new BigDecimal("22.5"),
                EFFICIENCY,
                at("18:00"),
                at("23:00"),
                hourly("18:00", "1.00", "0.90", "0.80", "0.70", "0.60"));

        OptimizationResult.Success success = success(result);
        assertThat(success.recommendedStartAt()).isEqualTo(at("20:30"));
        assertThat(success.recommendedEndAt()).isEqualTo(at("23:00"));
    }

    @Test
    void whenLateHoursAreExpensiveItStartsAtTheEarliestAllowedTime() {
        OptimizationResult result = calculator.optimize(
                ev("10", "10"),
                new BigDecimal("22.5"),
                EFFICIENCY,
                at("18:00"),
                at("23:00"),
                hourly("18:00", "0.60", "0.70", "0.80", "0.90", "1.00"));

        assertThat(success(result).recommendedStartAt()).isEqualTo(at("18:00"));
    }

    @Test
    void proratesAPartialLeadingSlotWhenChargingStartsMidHour() {
        // 9 kWh / 9 kW = 60 minutes.
        OptimizationResult result = calculator.optimize(
                ev("10", "10"),
                new BigDecimal("9"),
                EFFICIENCY,
                at("18:30"),
                at("20:00"),
                hourly("18:00", "0.20", "1.00"));

        OptimizationResult.Success success = success(result);
        assertThat(success.recommendedStartAt()).isEqualTo(at("18:30"));
        assertThat(success.recommendedEndAt()).isEqualTo(at("19:30"));
        assertThat(success.slots()).hasSize(2);
        assertThat(success.slots().get(0).startsAt()).isEqualTo(at("18:30"));
        assertThat(success.slots().get(0).endsAt()).isEqualTo(at("19:00"));
        assertThat(success.slots().get(0).plannedEnergyKwh()).isEqualByComparingTo("5.00");
        assertThat(success.estimatedCostNok()).isEqualByComparingTo("6.0000"); // 5*0.20 + 5*1.00
    }

    @Test
    void roundsAFractionalChargingDurationUpToWholeMinutes() {
        // 10 kWh / 9 kW = 1.1111 h = 66.67 min -> 67.
        OptimizationResult result = calculator.optimize(
                ev("10", "10"),
                new BigDecimal("10"),
                EFFICIENCY,
                at("18:00"),
                at("23:00"),
                hourly("18:00", "0.50", "0.50", "0.50", "0.50", "0.50"));

        assertThat(success(result).estimatedDurationMinutes()).isEqualTo(67);
    }

    @Test
    void returnsDeadlineTooSoonWhenTheWindowIsShorterThanTheChargingTime() {
        OptimizationResult result = calculator.optimize(
                ev("10", "10"),
                new BigDecimal("22.5"), // needs 150 minutes
                EFFICIENCY,
                at("18:00"),
                at("20:00"), // only 120 minutes available
                hourly("18:00", "0.50", "0.50", "0.50"));

        assertThat(result).isInstanceOf(OptimizationResult.Infeasible.class);
        assertThat(((OptimizationResult.Infeasible) result).reason())
                .isEqualTo(OptimizationResult.Reason.DEADLINE_TOO_SOON);
    }

    @Test
    void returnsInsufficientPriceDataWhenNoPricesAreGiven() {
        OptimizationResult result = calculator.optimize(
                ev("10", "10"),
                new BigDecimal("22.5"),
                EFFICIENCY,
                at("18:00"),
                at("23:00"),
                List.of());

        assertThat(result).isInstanceOf(OptimizationResult.Infeasible.class);
        assertThat(((OptimizationResult.Infeasible) result).reason())
                .isEqualTo(OptimizationResult.Reason.INSUFFICIENT_PRICE_DATA);
    }

    @Test
    void returnsNoContinuousWindowWhenAGapBreaksEveryCandidateWindow() {
        List<PricePoint> withGap = new ArrayList<>();
        withGap.add(price("18:00", "19:00", "0.50"));
        withGap.add(price("19:00", "20:00", "0.50"));
        // 20:00-21:00 missing
        withGap.add(price("21:00", "22:00", "0.50"));
        withGap.add(price("22:00", "23:00", "0.50"));

        OptimizationResult result = calculator.optimize(
                ev("10", "10"),
                new BigDecimal("22.5"), // needs 150 minutes, no gap-free run is that long
                EFFICIENCY,
                at("18:00"),
                at("23:00"),
                withGap);

        assertThat(result).isInstanceOf(OptimizationResult.Infeasible.class);
        assertThat(((OptimizationResult.Infeasible) result).reason())
                .isEqualTo(OptimizationResult.Reason.NO_CONTINUOUS_WINDOW);
    }

    @Test
    void fallsBackToZeroSavingsWhenTheImmediateWindowHasNoPriceData() {
        // Prices only start at 19:00, so charging "right now" from 18:20 cannot be priced.
        OptimizationResult result = calculator.optimize(
                ev("10", "10"),
                new BigDecimal("9"), // 60 minutes
                EFFICIENCY,
                at("18:20"),
                at("22:00"),
                hourly("19:00", "0.50", "0.40", "0.60"));

        OptimizationResult.Success success = success(result);
        assertThat(success.recommendedStartAt()).isEqualTo(at("20:00"));
        assertThat(success.baselineCostNok()).isEqualByComparingTo(success.estimatedCostNok());
        assertThat(success.expectedSavingsNok()).isEqualByComparingTo("0.0000");
    }

    @Test
    void measuresSlotLengthByRealDurationSoADoubledDstHourIsFullyUsed() {
        // Norwegian fall-back night: this single slot really spans two hours (+02:00 -> +01:00).
        PricePoint doubledHour = new PricePoint(
                OffsetDateTime.parse("2026-10-25T02:00:00+02:00"),
                OffsetDateTime.parse("2026-10-25T03:00:00+01:00"),
                new BigDecimal("0.10"));

        OptimizationResult result = calculator.optimize(
                ev("10", "10"),
                new BigDecimal("18"), // 18 / 9 = 2 hours
                EFFICIENCY,
                OffsetDateTime.parse("2026-10-25T02:00:00+02:00"),
                OffsetDateTime.parse("2026-10-25T03:00:00+01:00"),
                List.of(doubledHour));

        OptimizationResult.Success success = success(result);
        assertThat(success.slots()).hasSize(1);
        assertThat(success.slots().get(0).plannedEnergyKwh()).isEqualByComparingTo("20.00");
        assertThat(success.estimatedCostNok()).isEqualByComparingTo("2.0000");
    }

    private static OptimizationResult.Success success(OptimizationResult result) {
        assertThat(result).isInstanceOf(OptimizationResult.Success.class);
        return (OptimizationResult.Success) result;
    }

    private static EvSnapshot ev(String maxAcKw, String chargerKw) {
        return new EvSnapshot("Car", "Make", "Model",
                new BigDecimal("80.00"), new BigDecimal(maxAcKw), new BigDecimal(chargerKw));
    }

    /** An {@code HH:mm} wall-clock time on 2026-08-24 in Norwegian summer time (+02:00). */
    private static OffsetDateTime at(String hhmm) {
        return OffsetDateTime.parse("2026-08-24T" + hhmm + ":00+02:00");
    }

    private static PricePoint price(String startHhmm, String endHhmm, String pricePerKwh) {
        return new PricePoint(at(startHhmm), at(endHhmm), new BigDecimal(pricePerKwh));
    }

    private static List<PricePoint> hourly(String firstStartHhmm, String... pricesPerKwh) {
        List<PricePoint> points = new ArrayList<>();
        int hour = Integer.parseInt(firstStartHhmm.substring(0, firstStartHhmm.indexOf(':')));
        int minute = Integer.parseInt(firstStartHhmm.substring(firstStartHhmm.indexOf(':') + 1));
        OffsetDateTime cursor = OffsetDateTime.parse(
                "2026-08-24T%02d:%02d:00+02:00".formatted(hour, minute));
        for (String price : pricesPerKwh) {
            OffsetDateTime next = cursor.plusHours(1);
            points.add(new PricePoint(cursor, next, new BigDecimal(price)));
            cursor = next;
        }
        return points;
    }
}
