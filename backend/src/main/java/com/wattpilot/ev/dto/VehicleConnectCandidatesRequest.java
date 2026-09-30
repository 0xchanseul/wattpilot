package com.wattpilot.ev.dto;

import jakarta.validation.constraints.NotBlank;

/** Sent by the callback page right after the Smartcar Connect redirect returns. */
public record VehicleConnectCandidatesRequest(
        @NotBlank String state,
        @NotBlank String smartcarUserId
) {
}
