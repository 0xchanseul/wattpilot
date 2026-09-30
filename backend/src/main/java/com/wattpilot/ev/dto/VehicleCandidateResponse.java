package com.wattpilot.ev.dto;

import com.wattpilot.integration.smartcar.dto.SmartcarVehicleCandidate;

/** One vehicle offered as a link candidate on the picker screen. */
public record VehicleCandidateResponse(
        String smartcarVehicleId,
        String make,
        String model,
        Integer year
) {
    public static VehicleCandidateResponse from(SmartcarVehicleCandidate candidate) {
        return new VehicleCandidateResponse(
                candidate.smartcarVehicleId(), candidate.make(), candidate.model(), candidate.year());
    }
}
