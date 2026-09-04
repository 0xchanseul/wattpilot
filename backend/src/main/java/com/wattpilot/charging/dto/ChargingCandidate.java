package com.wattpilot.charging.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * One feasible continuous charging window produced by {@link com.wattpilot.charging.service.ChargingWindowCalculator}.
 *
 * <p>Candidates are returned ordered by {@code estimatedCostNok} ascending, then by
 * {@code recommendedStartAt} ascending, with {@code rank} assigned 1-based in that order. The same
 * value object backs both the preview response and the final-schedule candidate matching, so a
 * candidate never has to be recomputed differently for the two paths.
 */
public record ChargingCandidate(
        int rank,
        OffsetDateTime recommendedStartAt,
        OffsetDateTime recommendedEndAt,
        BigDecimal expectedEnergyKwh,
        BigDecimal estimatedCostNok,
        BigDecimal baselineCostNok,
        BigDecimal expectedSavingsNok,
        List<ChargingPlanSlot> slots
) {
}
