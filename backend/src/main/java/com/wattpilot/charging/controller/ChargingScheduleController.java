package com.wattpilot.charging.controller;

import com.wattpilot.charging.dto.ChargingScheduleResponse;
import com.wattpilot.charging.dto.CreateChargingScheduleRequest;
import com.wattpilot.charging.service.ChargingScheduleService;
import com.wattpilot.common.response.PageResponse;
import com.wattpilot.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/charging-schedules")
@Tag(name = "Scheduler", description = "Charging schedule lifecycle")
public class ChargingScheduleController {

    private final ChargingScheduleService chargingScheduleService;

    public ChargingScheduleController(ChargingScheduleService chargingScheduleService) {
        this.chargingScheduleService = chargingScheduleService;
    }

    @Operation(summary = "Confirm a previewed candidate as a charging schedule")
    @PostMapping
    public ResponseEntity<ChargingScheduleResponse> createChargingSchedule(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody CreateChargingScheduleRequest request) {
        ChargingScheduleResponse schedule =
                chargingScheduleService.createSchedule(authenticatedUser.userId(), request);
        return ResponseEntity.created(URI.create("/api/v1/charging-schedules/" + schedule.id())).body(schedule);
    }

    @Operation(summary = "List my charging schedules")
    @GetMapping
    public ResponseEntity<PageResponse<ChargingScheduleResponse>> listChargingSchedules(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(chargingScheduleService.listSchedules(authenticatedUser.userId(), pageable));
    }

    @Operation(summary = "Get a charging schedule")
    @GetMapping("/{scheduleId}")
    public ResponseEntity<ChargingScheduleResponse> getChargingSchedule(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long scheduleId) {
        return ResponseEntity.ok(chargingScheduleService.getSchedule(authenticatedUser.userId(), scheduleId));
    }
}
