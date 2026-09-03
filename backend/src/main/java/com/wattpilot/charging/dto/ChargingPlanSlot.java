package com.wattpilot.charging.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * One price slot inside the selected continuous charging window, matching the {@code ChargingPlanSlot}
 * schema in docs/openapi.yaml.
 *
 * <p>The first and last slot of a window may be partial, so {@code plannedEnergyKwh} and
 * {@code expectedCostNok} are prorated to the covered minutes. {@code plannedEnergyKwh} is grid-side
 * energy (what the charger draws), so the slot energies sum to {@code requiredEnergyKwh / efficiency}.
 */
public record ChargingPlanSlot(
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        BigDecimal pricePerKwh,
        BigDecimal plannedEnergyKwh,
        BigDecimal expectedCostNok
) {
}
