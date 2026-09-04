package com.wattpilot.charging.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Outcome of a candidate calculation.
 *
 * <p>{@link Feasible} carries every feasible continuous charging window (cost-ranked); {@link Infeasible}
 * means the request was well-formed but no window could be produced. Neither variant touches the
 * database — persistence happens only when the user confirms a specific candidate.
 */
public sealed interface ChargingCandidatesResult {

    /**
     * @param calculatedEnergyKwh       battery-side energy to add (target); efficiency not applied
     * @param effectiveChargingPowerKw  {@code min(maxAcChargingPowerKw, defaultChargerPowerKw)}; efficiency not applied
     * @param estimatedDurationMinutes  charging time, rounded up to whole minutes; identical for every candidate
     * @param candidates                feasible windows, ordered by cost then start, {@code rank} set 1-based,
     *                                  windows with the same start and end de-duplicated
     */
    record Feasible(
            BigDecimal calculatedEnergyKwh,
            BigDecimal effectiveChargingPowerKw,
            int estimatedDurationMinutes,
            List<ChargingCandidate> candidates
    ) implements ChargingCandidatesResult {
    }

    record Infeasible(OptimizationResult.Reason reason, String detail) implements ChargingCandidatesResult {
    }
}
