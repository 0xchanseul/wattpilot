package com.wattpilot.ev.dto;

import com.wattpilot.ev.entity.VehicleConnection;

import java.time.OffsetDateTime;

/** Link metadata only, no live telemetry. Matches the {@code VehicleConnection} schema in
 * docs/openapi.yaml and deliberately omits every Smartcar id, which are internal correlation
 * values with no meaning to the frontend. */
public record VehicleConnectionResponse(
        Long evId,
        String make,
        String model,
        Integer year,
        OffsetDateTime connectedAt
) {
    public static VehicleConnectionResponse from(VehicleConnection connection) {
        return new VehicleConnectionResponse(
                connection.getEvId(),
                connection.getVehicleMake(),
                connection.getVehicleModel(),
                connection.getVehicleYear(),
                connection.getCreatedAt());
    }
}
