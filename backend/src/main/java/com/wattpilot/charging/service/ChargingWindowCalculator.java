package com.wattpilot.charging.service;

import com.wattpilot.charging.dto.ChargingPlanSlot;
import com.wattpilot.charging.dto.EvSnapshot;
import com.wattpilot.charging.dto.OptimizationResult;
import com.wattpilot.electricity.dto.PricePoint;
import com.wattpilot.electricity.service.ElectricityPriceService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Pure charging-window optimization: no Spring collaborators, no I/O.
 *
 * <p>Given the energy target, the usable time window and the hourly prices, it finds the cheapest
 * gap-free continuous window that satisfies the required charging duration and finishes by the
 * deadline.
 *
 * <p><b>Why only "breakpoints" are checked.</b> The charging window has a fixed width. Sliding it
 * forward by a small step trades a sliver of the leaving hour for an equal sliver of the entering
 * hour, and within a single hour the price is constant — so cost changes at a constant rate between
 * hour boundaries, i.e. cost is piecewise-linear in the window start. The minimum of a piecewise-
 * linear function is always at a breakpoint: the earliest start, the latest start, or a price-slot
 * boundary in between.
 *
 * <p><b>Efficiency.</b> The configured efficiency only lengthens the duration. The billed energy and
 * every cost are computed on the full charger draw {@code min(maxAcChargingPowerKw,
 * defaultChargerPowerKw)}, so the slot energies sum to {@code requiredEnergyKwh / efficiency}.
 */
@Component
public class ChargingWindowCalculator {

    /** Display zone for the recommended window and its slots; matches every other price-derived response. */
    private static final ZoneId DISPLAY_ZONE = ElectricityPriceService.PRICE_ZONE;

    private static final int ENERGY_SCALE = 2; // NUMERIC(8,2)
    private static final int COST_SCALE = 4;   // NUMERIC(12,4)
    private static final int INTERNAL_SCALE = 10;
    private static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);

    /**
     * @param evSnapshot        EV figures to charge with
     * @param requiredEnergyKwh battery-side energy to add (already validated {@code > 0})
     * @param efficiency        fraction of grid energy that reaches the battery
     * @param earliestStart     earliest the charging may begin (already clamped to now, minute-aligned)
     * @param deadline          charging must finish by this instant (minute-aligned)
     * @param prices            hours overlapping {@code [earliestStart, deadline)}, ordered by start
     */
    public OptimizationResult optimize(EvSnapshot evSnapshot,
                                       BigDecimal requiredEnergyKwh,
                                       BigDecimal efficiency,
                                       OffsetDateTime earliestStart,
                                       OffsetDateTime deadline,
                                       List<PricePoint> prices) {

        BigDecimal deliveredPowerKw = evSnapshot.maxAcChargingPowerKw().min(evSnapshot.defaultChargerPowerKw());
        BigDecimal effectivePowerKw = deliveredPowerKw.multiply(efficiency);

        int durationMinutes = Math.max(1, requiredEnergyKwh
                .multiply(MINUTES_PER_HOUR)
                .divide(effectivePowerKw, INTERNAL_SCALE, RoundingMode.HALF_UP)
                .setScale(0, RoundingMode.CEILING)
                .intValueExact());
        Duration chargingDuration = Duration.ofMinutes(durationMinutes);

        OffsetDateTime latestStart = deadline.minus(chargingDuration);
        if (latestStart.isBefore(earliestStart)) {
            long availableMinutes = Math.max(0, Duration.between(earliestStart, deadline).toMinutes());
            return new OptimizationResult.Infeasible(OptimizationResult.Reason.DEADLINE_TOO_SOON,
                    "Charging needs %d minutes but only %d are available before the deadline."
                            .formatted(durationMinutes, availableMinutes));
        }

        if (prices.isEmpty()) {
            return new OptimizationResult.Infeasible(OptimizationResult.Reason.INSUFFICIENT_PRICE_DATA,
                    "No stored electricity prices cover the requested charging window.");
        }

        List<ChargingPlanSlot> bestSlots = null;
        OffsetDateTime bestStart = null;
        BigDecimal bestCost = null;
        for (OffsetDateTime candidate : candidateStarts(earliestStart, latestStart, prices)) {
            List<ChargingPlanSlot> slots =
                    buildSlots(candidate, candidate.plus(chargingDuration), deliveredPowerKw, prices);
            if (slots == null) {
                continue;
            }
            BigDecimal cost = sum(slots, ChargingPlanSlot::expectedCostNok, COST_SCALE);
            if (bestCost == null || cost.compareTo(bestCost) < 0) {
                bestCost = cost;
                bestSlots = slots;
                bestStart = candidate;
            }
        }

        if (bestSlots == null) {
            return new OptimizationResult.Infeasible(OptimizationResult.Reason.NO_CONTINUOUS_WINDOW,
                    "No gap-free run of stored prices is long enough to charge within the window.");
        }

        BigDecimal expectedEnergy = sum(bestSlots, ChargingPlanSlot::plannedEnergyKwh, ENERGY_SCALE);

        List<ChargingPlanSlot> immediateSlots =
                buildSlots(earliestStart, earliestStart.plus(chargingDuration), deliveredPowerKw, prices);
        BigDecimal baselineCost = immediateSlots != null
                ? sum(immediateSlots, ChargingPlanSlot::expectedCostNok, COST_SCALE)
                : bestCost;

        return new OptimizationResult.Success(
                evSnapshot,
                requiredEnergyKwh.setScale(ENERGY_SCALE, RoundingMode.HALF_UP),
                deliveredPowerKw.setScale(ENERGY_SCALE, RoundingMode.HALF_UP),
                durationMinutes,
                toDisplayZone(bestStart),
                toDisplayZone(bestStart.plus(chargingDuration)),
                expectedEnergy,
                bestCost,
                baselineCost,
                baselineCost.subtract(bestCost),
                bestSlots);
    }

    /**
     * The breakpoints where the piecewise-linear cost function can bend, clamped to the feasible
     * start range: the earliest start, the latest start, and every price-slot boundary in between.
     */
    private static List<OffsetDateTime> candidateStarts(OffsetDateTime earliestStart, OffsetDateTime latestStart,
                                                        List<PricePoint> prices) {
        NavigableSet<OffsetDateTime> starts = new TreeSet<>(Comparator.comparing(OffsetDateTime::toInstant));
        starts.add(earliestStart);
        starts.add(latestStart);
        for (PricePoint price : prices) {
            addIfWithin(starts, price.startsAt(), earliestStart, latestStart);
            addIfWithin(starts, price.endsAt(), earliestStart, latestStart);
        }
        return new ArrayList<>(starts);
    }

    private static void addIfWithin(NavigableSet<OffsetDateTime> starts, OffsetDateTime value,
                                    OffsetDateTime lowerInclusive, OffsetDateTime upperInclusive) {
        if (!value.toInstant().isBefore(lowerInclusive.toInstant())
                && !value.toInstant().isAfter(upperInclusive.toInstant())) {
            starts.add(value);
        }
    }

    /**
     * Slices {@code [windowStart, windowEnd]} into the price slots it overlaps, prorating a partial
     * leading or trailing slot. Returns {@code null} when the window is not fully covered by a
     * gap-free run of prices.
     */
    private static List<ChargingPlanSlot> buildSlots(OffsetDateTime windowStart, OffsetDateTime windowEnd,
                                                     BigDecimal deliveredPowerKw, List<PricePoint> prices) {
        List<ChargingPlanSlot> slots = new ArrayList<>();
        OffsetDateTime cursor = windowStart;
        for (PricePoint price : prices) {
            if (!price.endsAt().toInstant().isAfter(cursor.toInstant())) {
                continue; // slot ends at or before the cursor: nothing to take
            }
            if (price.startsAt().toInstant().isAfter(cursor.toInstant())) {
                return null; // gap between the cursor and the next slot
            }
            OffsetDateTime slotEnd = earlier(price.endsAt(), windowEnd);
            long minutes = Duration.between(cursor, slotEnd).toMinutes();
            if (minutes > 0) {
                BigDecimal hours = BigDecimal.valueOf(minutes)
                        .divide(MINUTES_PER_HOUR, INTERNAL_SCALE, RoundingMode.HALF_UP);
                BigDecimal energy = deliveredPowerKw.multiply(hours);
                slots.add(new ChargingPlanSlot(
                        toDisplayZone(cursor),
                        toDisplayZone(slotEnd),
                        price.pricePerKwh(),
                        energy.setScale(ENERGY_SCALE, RoundingMode.HALF_UP),
                        energy.multiply(price.pricePerKwh()).setScale(COST_SCALE, RoundingMode.HALF_UP)));
            }
            cursor = slotEnd;
            if (!cursor.toInstant().isBefore(windowEnd.toInstant())) {
                return slots; // window fully covered
            }
        }
        return null; // ran out of prices before the window end
    }

    private static OffsetDateTime earlier(OffsetDateTime a, OffsetDateTime b) {
        return a.toInstant().isBefore(b.toInstant()) ? a : b;
    }

    private static BigDecimal sum(List<ChargingPlanSlot> slots,
                                  Function<ChargingPlanSlot, BigDecimal> field, int scale) {
        return slots.stream()
                .map(field)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(scale, RoundingMode.HALF_UP);
    }

    private static OffsetDateTime toDisplayZone(OffsetDateTime value) {
        return value.atZoneSameInstant(DISPLAY_ZONE).toOffsetDateTime();
    }
}
