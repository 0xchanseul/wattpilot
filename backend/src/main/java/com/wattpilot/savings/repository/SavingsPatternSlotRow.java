package com.wattpilot.savings.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * One charging plan slot of a {@code COMPLETED} session's realized charge, from
 * {@link SavingsRepository}, backing {@code GET /savings/patterns}.
 *
 * <p>{@code sessionActualEnergyKwh}/{@code sessionBaselineCostNok} are the owning session's own
 * totals, repeated on every one of its slot rows, so {@code SavingsService} can prorate the
 * session's single baseline figure across its slots by each slot's share of the session's actual
 * energy — the plan/session model has no per-slot baseline of its own.
 */
public record SavingsPatternSlotRow(
        Long sessionId,
        OffsetDateTime slotStartAt,
        BigDecimal plannedEnergyKwh,
        BigDecimal expectedCostNok,
        BigDecimal sessionActualEnergyKwh,
        BigDecimal sessionBaselineCostNok
) {
}
