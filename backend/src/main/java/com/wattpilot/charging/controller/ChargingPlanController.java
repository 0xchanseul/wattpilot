package com.wattpilot.charging.controller;

import com.wattpilot.charging.dto.ChargingPlanPreviewResponse;
import com.wattpilot.charging.dto.ChargingPlanResponse;
import com.wattpilot.charging.dto.CreateChargingPlanPreviewRequest;
import com.wattpilot.charging.entity.ChargingPlanStatus;
import com.wattpilot.charging.service.ChargingPlanPreviewService;
import com.wattpilot.charging.service.ChargingPlanService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/charging-plans")
@Tag(name = "Charging Optimization", description = "Charging plan preview and stored recommendations")
public class ChargingPlanController {

    private final ChargingPlanPreviewService previewService;
    private final ChargingPlanService chargingPlanService;

    public ChargingPlanController(ChargingPlanPreviewService previewService, ChargingPlanService chargingPlanService) {
        this.previewService = previewService;
        this.chargingPlanService = chargingPlanService;
    }

    @Operation(summary = "Preview optimized charging candidates")
    @PostMapping("/preview")
    public ResponseEntity<ChargingPlanPreviewResponse> previewChargingPlan(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody CreateChargingPlanPreviewRequest request) {
        return ResponseEntity.ok(previewService.preview(authenticatedUser.userId(), request));
    }

    @Operation(summary = "List my charging plans")
    @GetMapping
    public ResponseEntity<PageResponse<ChargingPlanResponse>> listChargingPlans(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(name = "evId", required = false) Long evId,
            @RequestParam(name = "status", required = false) ChargingPlanStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(chargingPlanService.listPlans(authenticatedUser.userId(), evId, status, pageable));
    }

    @Operation(summary = "Get a charging plan")
    @GetMapping("/{planId}")
    public ResponseEntity<ChargingPlanResponse> getChargingPlan(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long planId) {
        return ResponseEntity.ok(chargingPlanService.getPlan(authenticatedUser.userId(), planId));
    }
}
