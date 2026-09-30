package com.wattpilot.ev.dto;

import com.wattpilot.integration.smartcar.dto.SmartcarTelemetry;

import java.time.OffsetDateTime;

/** Live read from the connected vehicle. Any field may be {@code null} if that signal is
 * unsupported by the vehicle or the read failed for it specifically. Intentionally has no relation
 * to Mock Charging's own simulated progress (see docs/mvp-scope.md) — the two are independent. */
public record VehicleTelemetryResponse(
        Double stateOfChargePercent,
        Double rangeKm,
        Boolean isPluggedIn,
        Boolean isCharging,
        OffsetDateTime retrievedAt
) {
    public static VehicleTelemetryResponse from(SmartcarTelemetry telemetry) {
        return new VehicleTelemetryResponse(
                telemetry.stateOfChargePercent(),
                telemetry.rangeKm(),
                telemetry.isPluggedIn(),
                telemetry.isCharging(),
                telemetry.retrievedAt());
    }
}
