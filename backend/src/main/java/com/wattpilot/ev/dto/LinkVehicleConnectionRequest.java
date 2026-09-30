package com.wattpilot.ev.dto;

import jakarta.validation.constraints.NotBlank;

/** The vehicle the user picked (or the only one returned) on the connect-candidates screen. */
public record LinkVehicleConnectionRequest(
        @NotBlank String state,
        @NotBlank String smartcarUserId,
        @NotBlank String smartcarVehicleId
) {
}
