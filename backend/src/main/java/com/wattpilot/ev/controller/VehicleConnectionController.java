package com.wattpilot.ev.controller;

import com.wattpilot.common.security.AuthenticatedUser;
import com.wattpilot.ev.dto.ConnectUrlResponse;
import com.wattpilot.ev.dto.LinkVehicleConnectionRequest;
import com.wattpilot.ev.dto.VehicleConnectCandidatesRequest;
import com.wattpilot.ev.dto.VehicleConnectCandidatesResponse;
import com.wattpilot.ev.dto.VehicleConnectionResponse;
import com.wattpilot.ev.dto.VehicleTelemetryResponse;
import com.wattpilot.ev.service.VehicleConnectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only Smartcar vehicle telemetry (V1.5). Every endpoint requires the caller's own bearer
 * token: the Smartcar Connect redirect lands on a protected SPA route, which calls
 * {@link #candidates} itself, so there is no public callback endpoint here (see the plan).
 */
@RestController
@Tag(name = "Vehicle Connection", description = "Read-only Smartcar vehicle telemetry, linked per EV")
public class VehicleConnectionController {

    private final VehicleConnectionService vehicleConnectionService;

    public VehicleConnectionController(VehicleConnectionService vehicleConnectionService) {
        this.vehicleConnectionService = vehicleConnectionService;
    }

    @Operation(summary = "Get a Smartcar Connect URL for an EV")
    @PostMapping("/api/v1/evs/{evId}/vehicle-connection/connect-url")
    public ResponseEntity<ConnectUrlResponse> getConnectUrl(@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
                                                            @PathVariable Long evId) {
        String url = vehicleConnectionService.buildConnectUrl(authenticatedUser.userId(), evId);
        return ResponseEntity.ok(new ConnectUrlResponse(url));
    }

    @Operation(summary = "List the vehicles available to link after returning from Smartcar Connect")
    @PostMapping("/api/v1/vehicle-connections/candidates")
    public ResponseEntity<VehicleConnectCandidatesResponse> getCandidates(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody VehicleConnectCandidatesRequest request) {
        return ResponseEntity.ok(vehicleConnectionService.candidates(authenticatedUser.userId(), request));
    }

    @Operation(summary = "Link an EV to a Smartcar vehicle")
    @PostMapping("/api/v1/evs/{evId}/vehicle-connection")
    public ResponseEntity<VehicleConnectionResponse> link(@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
                                                           @PathVariable Long evId,
                                                           @Valid @RequestBody LinkVehicleConnectionRequest request) {
        VehicleConnectionResponse response = vehicleConnectionService.link(authenticatedUser.userId(), evId, request);
        return ResponseEntity.status(201).body(response);
    }

    @Operation(summary = "Get an EV's vehicle connection (link metadata only, no live Smartcar call)")
    @GetMapping("/api/v1/evs/{evId}/vehicle-connection")
    public ResponseEntity<VehicleConnectionResponse> getConnection(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser, @PathVariable Long evId) {
        return ResponseEntity.ok(vehicleConnectionService.get(authenticatedUser.userId(), evId));
    }

    @Operation(summary = "Read live telemetry (battery, range, plug/charging status) from the connected vehicle")
    @GetMapping("/api/v1/evs/{evId}/vehicle-connection/telemetry")
    public ResponseEntity<VehicleTelemetryResponse> getTelemetry(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser, @PathVariable Long evId) {
        return ResponseEntity.ok(vehicleConnectionService.telemetry(authenticatedUser.userId(), evId));
    }

    @Operation(summary = "Disconnect an EV's Smartcar vehicle")
    @DeleteMapping("/api/v1/evs/{evId}/vehicle-connection")
    public ResponseEntity<Void> disconnect(@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
                                           @PathVariable Long evId) {
        vehicleConnectionService.disconnect(authenticatedUser.userId(), evId);
        return ResponseEntity.noContent().build();
    }
}
