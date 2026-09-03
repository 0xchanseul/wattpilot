package com.wattpilot.charging.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Outcome of one optimization attempt.
 *
 * <p>{@link Success} carries a fully populated recommendation. {@link Infeasible} means the request
 * was well-formed but no continuous charging window could be produced; genuinely invalid input is
 * rejected with an exception before a result is returned.
 *
 * <p>The current V1 step does not persist this. A later step stores it as a {@code charging_plans}
 * row (SUCCEEDED / FAILED) and exposes it over HTTP, mapping {@link Infeasible} to 422.
 */
public sealed interface OptimizationResult {

    /**
     * @param evSnapshot                EV figures the calculation used
     * @param calculatedEnergyKwh       energy to add to the battery (battery-side target)
     * @param effectiveChargingPowerKw  {@code min(maxAcChargingPowerKw, defaultChargerPowerKw)} — the
     *                                  charger draw, with charging efficiency NOT applied
     * @param estimatedDurationMinutes  charging time, rounded up to whole minutes
     * @param recommendedStartAt        start of the cheapest continuous window
     * @param recommendedEndAt          {@code recommendedStartAt + estimatedDurationMinutes}
     * @param expectedEnergyKwh         grid-side energy drawn over the window
     *                                  ({@code calculatedEnergyKwh / efficiency}); equals the sum of the slot energies
     * @param estimatedCostNok          cost of the recommended window; sum of the slot costs
     * @param baselineCostNok           cost of charging immediately from the earliest start; falls back
     *                                  to {@code estimatedCostNok} when that immediate window has no price data
     * @param expectedSavingsNok        {@code baselineCostNok - estimatedCostNok}
     * @param slots                     consecutive price slots making up the window, first/last possibly partial
     */
    record Success(
            EvSnapshot evSnapshot,
            BigDecimal calculatedEnergyKwh,
            BigDecimal effectiveChargingPowerKw,
            int estimatedDurationMinutes,
            OffsetDateTime recommendedStartAt,
            OffsetDateTime recommendedEndAt,
            BigDecimal expectedEnergyKwh,
            BigDecimal estimatedCostNok,
            BigDecimal baselineCostNok,
            BigDecimal expectedSavingsNok,
            List<ChargingPlanSlot> slots
    ) implements OptimizationResult {
    }

    record Infeasible(Reason reason, String detail) implements OptimizationResult {
    }

    enum Reason {
        /** Not enough time between the earliest start and the deadline to deliver the energy. */
        DEADLINE_TOO_SOON,
        /** No stored electricity prices cover the charging window. */
        INSUFFICIENT_PRICE_DATA,
        /** Prices exist but no gap-free run of slots is long enough within the window. */
        NO_CONTINUOUS_WINDOW
    }
}
