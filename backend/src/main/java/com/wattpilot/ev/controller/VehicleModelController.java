package com.wattpilot.ev.controller;

import com.wattpilot.ev.dto.VehicleModelResponse;
import com.wattpilot.ev.service.VehicleModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/vehicle-models")
@Tag(name = "EV", description = "Manually registered electric vehicles")
public class VehicleModelController {

    private final VehicleModelService vehicleModelService;

    public VehicleModelController(VehicleModelService vehicleModelService) {
        this.vehicleModelService = vehicleModelService;
    }

    @Operation(summary = "List vehicle specification presets")
    @GetMapping
    public ResponseEntity<List<VehicleModelResponse>> listVehicleModels(
            @RequestParam(name = "q", required = false) String query) {
        return ResponseEntity.ok(vehicleModelService.search(query));
    }
}
