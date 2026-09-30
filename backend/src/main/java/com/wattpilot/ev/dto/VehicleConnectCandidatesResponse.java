package com.wattpilot.ev.dto;

import java.util.List;

public record VehicleConnectCandidatesResponse(
        Long evId,
        List<VehicleCandidateResponse> vehicles
) {
}
