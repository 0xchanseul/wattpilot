package com.wattpilot.savings.controller;

import com.wattpilot.common.security.AuthenticatedUser;
import com.wattpilot.savings.dto.DailySavings;
import com.wattpilot.savings.dto.Granularity;
import com.wattpilot.savings.dto.SavingsSummary;
import com.wattpilot.savings.service.SavingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/savings")
@Tag(name = "Savings", description = "Cost and savings summaries")
public class SavingsController {

    private final SavingsService savingsService;

    public SavingsController(SavingsService savingsService) {
        this.savingsService = savingsService;
    }

    @Operation(summary = "Get savings summary")
    @GetMapping("/summary")
    public ResponseEntity<SavingsSummary> getSavingsSummary(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "evId", required = false) Long evId) {
        return ResponseEntity.ok(savingsService.getSummary(authenticatedUser.userId(), from, to, evId));
    }

    @Operation(summary = "Get daily savings trend")
    @GetMapping("/daily")
    public ResponseEntity<List<DailySavings>> getDailySavings(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "evId", required = false) Long evId,
            @RequestParam(name = "granularity", required = false) Granularity granularity) {
        return ResponseEntity.ok(savingsService.getDaily(authenticatedUser.userId(), from, to, evId, granularity));
    }
}
