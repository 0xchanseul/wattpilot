package com.wattpilot.integration.smartcar.dto;

import java.time.OffsetDateTime;

/**
 * Live read of a connected vehicle, assembled from four independent signal calls. Any field may be
 * {@code null} if that signal failed or is unsupported by the vehicle.
 */
public record SmartcarTelemetry(
        Double stateOfChargePercent,
        Double rangeKm,
        Boolean isPluggedIn,
        Boolean isCharging,
        OffsetDateTime retrievedAt
) {
}
