package com.wattpilot.charging.service;

import com.wattpilot.charging.entity.ChargingPlanSlot;
import com.wattpilot.electricity.entity.ElectricityPrice;
import com.wattpilot.electricity.service.ElectricityPriceService;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps between the calculator's slot value objects ({@link com.wattpilot.charging.dto.ChargingPlanSlot})
 * and {@code charging_plan_slots} rows ({@link ChargingPlanSlot}). No calculation happens here — energy
 * and cost are copied verbatim so the persisted slot sums stay exactly equal to the plan aggregates.
 */
final class ChargingSlotMapper {

    private ChargingSlotMapper() {
    }

    /**
     * Persistable slot rows for a plan, in calculation order. Each row is linked to the
     * {@code electricity_prices} row it was priced from, matched by instant containment: a slot lies
     * entirely within exactly one stored hour.
     */
    static List<ChargingPlanSlot> toEntities(Long chargingPlanId,
                                             List<com.wattpilot.charging.dto.ChargingPlanSlot> slots,
                                             List<ElectricityPrice> pricesInWindow) {
        List<ChargingPlanSlot> entities = new ArrayList<>(slots.size());
        int sequenceNo = 1;
        for (com.wattpilot.charging.dto.ChargingPlanSlot slot : slots) {
            ElectricityPrice price = priceCovering(pricesInWindow, slot.startsAt(), slot.endsAt());
            entities.add(ChargingPlanSlot.of(chargingPlanId, price.getId(), slot.startsAt(), slot.endsAt(),
                    slot.pricePerKwh(), slot.plannedEnergyKwh(), slot.expectedCostNok(), sequenceNo++));
        }
        return entities;
    }

    static List<com.wattpilot.charging.dto.ChargingPlanSlot> toDtos(List<ChargingPlanSlot> slots) {
        return slots.stream()
                .map(slot -> new com.wattpilot.charging.dto.ChargingPlanSlot(
                        atDisplayZone(slot.getSlotStartAt()),
                        atDisplayZone(slot.getSlotEndAt()),
                        slot.getPricePerKwh(),
                        slot.getPlannedEnergyKwh(),
                        slot.getExpectedCostNok()))
                .toList();
    }

    private static OffsetDateTime atDisplayZone(OffsetDateTime value) {
        return value.atZoneSameInstant(ElectricityPriceService.PRICE_ZONE).toOffsetDateTime();
    }

    private static ElectricityPrice priceCovering(List<ElectricityPrice> prices, OffsetDateTime slotStart,
                                                  OffsetDateTime slotEnd) {
        Instant start = slotStart.toInstant();
        Instant end = slotEnd.toInstant();
        return prices.stream()
                .filter(price -> !price.getStartsAt().toInstant().isAfter(start)
                        && !price.getEndsAt().toInstant().isBefore(end))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No stored electricity price covers charging slot [%s, %s]".formatted(slotStart, slotEnd)));
    }
}
