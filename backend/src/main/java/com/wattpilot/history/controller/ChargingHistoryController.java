package com.wattpilot.history.controller;

import com.wattpilot.charging.entity.ChargingSessionStatus;
import com.wattpilot.common.security.AuthenticatedUser;
import com.wattpilot.history.dto.ChargingHistoryDetail;
import com.wattpilot.history.dto.ChargingHistoryListResponse;
import com.wattpilot.history.service.ChargingHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/charging-history")
@Tag(name = "Charging History", description = "Executed charging results and savings performance")
public class ChargingHistoryController {

    private final ChargingHistoryService chargingHistoryService;

    public ChargingHistoryController(ChargingHistoryService chargingHistoryService) {
        this.chargingHistoryService = chargingHistoryService;
    }

    @Operation(summary = "List my charging history with savings totals")
    @GetMapping
    public ResponseEntity<ChargingHistoryListResponse> listChargingHistory(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(name = "evId", required = false) Long evId,
            @RequestParam(name = "status", required = false) ChargingSessionStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                chargingHistoryService.listHistory(authenticatedUser.userId(), evId, status, pageable));
    }

    @Operation(summary = "Get one charging-history entry (the charging receipt)")
    @GetMapping("/{sessionId}")
    public ResponseEntity<ChargingHistoryDetail> getChargingHistoryEntry(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long sessionId) {
        return ResponseEntity.ok(chargingHistoryService.getHistoryDetail(authenticatedUser.userId(), sessionId));
    }
}
