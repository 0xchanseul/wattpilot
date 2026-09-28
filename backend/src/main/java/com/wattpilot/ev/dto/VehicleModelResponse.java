package com.wattpilot.ev.dto;

import com.wattpilot.ev.entity.VehicleModel;

import java.math.BigDecimal;

/**
 * Public view of a curated vehicle specification preset. Matches the {@code VehicleModel} schema in
 * docs/openapi.yaml.
 */
public record VehicleModelResponse(
        Long id,
        String manufacturer,
        String model,
        BigDecimal batteryCapacityKwh,
        BigDecimal maxAcChargingPowerKw
) {

    public static VehicleModelResponse from(VehicleModel vehicleModel) {
        return new VehicleModelResponse(
                vehicleModel.getId(),
                vehicleModel.getManufacturer(),
                vehicleModel.getModel(),
                vehicleModel.getBatteryCapacityKwh(),
                vehicleModel.getMaxAcChargingPowerKw());
    }
}
