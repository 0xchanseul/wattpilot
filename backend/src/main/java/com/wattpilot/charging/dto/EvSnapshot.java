package com.wattpilot.charging.dto;

import com.wattpilot.ev.entity.Ev;

import java.math.BigDecimal;

/**
 * Immutable copy of the EV figures the optimizer works from, matching the {@code EvSnapshot} schema
 * in docs/openapi.yaml. A later step persists it onto each charging plan so editing an EV never
 * rewrites past plans.
 */
public record EvSnapshot(
        String name,
        String manufacturer,
        String model,
        BigDecimal batteryCapacityKwh,
        BigDecimal maxAcChargingPowerKw,
        BigDecimal defaultChargerPowerKw
) {

    public static EvSnapshot from(Ev ev) {
        return new EvSnapshot(
                ev.getName(),
                ev.getManufacturer(),
                ev.getModel(),
                ev.getBatteryCapacityKwh(),
                ev.getMaxAcChargingPowerKw(),
                ev.getDefaultChargerPowerKw());
    }
}
