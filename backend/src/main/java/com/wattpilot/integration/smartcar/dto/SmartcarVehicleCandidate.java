package com.wattpilot.integration.smartcar.dto;

/** One vehicle in a Smartcar user's account, offered as a link candidate. */
public record SmartcarVehicleCandidate(
        String connectionId,
        String smartcarVehicleId,
        String make,
        String model,
        Integer year
) {
}
