package com.wattpilot.ev.dto;

import com.wattpilot.ev.entity.Ev;
import com.wattpilot.ev.entity.EvStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Public view of an EV. Matches the {@code Ev} schema in docs/openapi.yaml and deliberately omits
 * the owning user id. {@code locked} is read-only: it lets the client explain a blocked action up
 * front instead of waiting for a 403.
 */
public record EvResponse(
        Long id,
        String name,
        String manufacturer,
        String model,
        BigDecimal batteryCapacityKwh,
        BigDecimal maxAcChargingPowerKw,
        BigDecimal defaultChargerPowerKw,
        EvStatus status,
        boolean locked,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static EvResponse from(Ev ev) {
        return new EvResponse(
                ev.getId(),
                ev.getName(),
                ev.getManufacturer(),
                ev.getModel(),
                ev.getBatteryCapacityKwh(),
                ev.getMaxAcChargingPowerKw(),
                ev.getDefaultChargerPowerKw(),
                ev.getStatus(),
                ev.isLocked(),
                ev.getCreatedAt(),
                ev.getUpdatedAt());
    }
}
