package com.wattpilot.charging.service;

import com.wattpilot.charging.dto.ChargingCandidate;
import com.wattpilot.charging.dto.ChargingCandidatesResult;
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
import java.util.HashSet;
import java.util.List;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Pure charging-window optimization: no Spring collaborators, no I/O, no persistence.
 *
 * <p>Given the energy target, the usable time window and the hourly prices, it enumerates every
 * gap-free continuous window that satisfies the required charging duration and finishes by the
 * deadline, then ranks them by cost. {@link #calculateCandidates} returns the full ranked list (the
 * preview shows the cheapest few, the scheduler matches the user's pick against it);
 * {@link #optimize} is a thin adapter that returns only the cheapest window.
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
     * Every feasible continuous charging window, ranked cheapest first.
     *
     * @param evSnapshot        EV figures to charge with
     * @param requiredEnergyKwh battery-side energy to add (already validated {@code > 0})
     * @param efficiency        fraction of grid energy that reaches the battery
     * @param earliestStart     earliest the charging may begin (already clamped to now, minute-aligned)
     * @param deadline          charging must finish by this instant (minute-aligned)
     * @param prices            hours overlapping {@code [earliestStart, deadline)}, ordered by start
     */
    public ChargingCandidatesResult calculateCandidates(EvSnapshot evSnapshot,
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
            return new ChargingCandidatesResult.Infeasible(OptimizationResult.Reason.DEADLINE_TOO_SOON,
                    "Charging needs %d minutes but only %d are available before the deadline."
                            .formatted(durationMinutes, availableMinutes));
        }

        if (prices.isEmpty()) {
            return new ChargingCandidatesResult.Infeasible(OptimizationResult.Reason.INSUFFICIENT_PRICE_DATA,
                    "No stored electricity prices cover the requested charging window.");
        }

        List<Window> windows = new ArrayList<>();
        for (OffsetDateTime candidate : candidateStarts(earliestStart, latestStart, prices)) {
            List<ChargingPlanSlot> slots =
                    buildSlots(candidate, candidate.plus(chargingDuration), deliveredPowerKw, prices);
            if (slots == null) {
                continue;
            }
            windows.add(new Window(candidate, candidate.plus(chargingDuration), slots,
                    sum(slots, ChargingPlanSlot::expectedCostNok, COST_SCALE)));
        }

        if (windows.isEmpty()) {
            return new ChargingCandidatesResult.Infeasible(OptimizationResult.Reason.NO_CONTINUOUS_WINDOW,
                    "No gap-free run of stored prices is long enough to charge within the window.");
        }

        List<ChargingPlanSlot> immediateSlots =
                buildSlots(earliestStart, earliestStart.plus(chargingDuration), deliveredPowerKw, prices);
        BigDecimal cheapestCost = windows.stream().map(Window::cost).min(Comparator.naturalOrder()).orElseThrow();
        BigDecimal baselineCost = immediateSlots != null
                ? sum(immediateSlots, ChargingPlanSlot::expectedCostNok, COST_SCALE)
                : cheapestCost;

        windows.sort(Comparator.comparing(Window::cost).thenComparing(window -> window.start().toInstant()));

        List<ChargingCandidate> candidates = new ArrayList<>();
        Set<String> seenWindows = new HashSet<>();
        int rank = 1;
        for (Window window : windows) {
            if (!seenWindows.add(window.start().toInstant() + "|" + window.end().toInstant())) {
                continue; // a different breakpoint that lands on an already-seen window
            }
            candidates.add(new ChargingCandidate(
                    rank++,
                    toDisplayZone(window.start()),
                    toDisplayZone(window.end()),
                    sum(window.slots(), ChargingPlanSlot::plannedEnergyKwh, ENERGY_SCALE),
                    window.cost(),
                    baselineCost,
                    baselineCost.subtract(window.cost()),
                    window.slots()));
        }

        return new ChargingCandidatesResult.Feasible(
                requiredEnergyKwh.setScale(ENERGY_SCALE, RoundingMode.HALF_UP),
                deliveredPowerKw.setScale(ENERGY_SCALE, RoundingMode.HALF_UP),
                durationMinutes,
                candidates);
    }

    /**
     * The single cheapest continuous window, in the legacy {@link OptimizationResult} shape. Retained
     * so existing calculator/orchestration tests keep their exact assertions; new code calls
     * {@link #calculateCandidates}.
     */
    public OptimizationResult optimize(EvSnapshot evSnapshot,
                                       BigDecimal requiredEnergyKwh,
                                       BigDecimal efficiency,
                                       OffsetDateTime earliestStart,
                                       OffsetDateTime deadline,
                                       List<PricePoint> prices) {

        ChargingCandidatesResult result =
                calculateCandidates(evSnapshot, requiredEnergyKwh, efficiency, earliestStart, deadline, prices);
        if (result instanceof ChargingCandidatesResult.Infeasible infeasible) {
            return new OptimizationResult.Infeasible(infeasible.reason(), infeasible.detail());
        }

        ChargingCandidatesResult.Feasible feasible = (ChargingCandidatesResult.Feasible) result;
        ChargingCandidate best = feasible.candidates().get(0);
        return new OptimizationResult.Success(
                evSnapshot,
                feasible.calculatedEnergyKwh(),
                feasible.effectiveChargingPowerKw(),
                feasible.estimatedDurationMinutes(),
                best.recommendedStartAt(),
                best.recommendedEndAt(),
                best.expectedEnergyKwh(),
                best.estimatedCostNok(),
                best.baselineCostNok(),
                best.expectedSavingsNok(),
                best.slots());
    }

    /** A feasible window before it is turned into a ranked {@link ChargingCandidate}. */
    private record Window(OffsetDateTime start, OffsetDateTime end, List<ChargingPlanSlot> slots, BigDecimal cost) {
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
