package com.wattpilot.ev.controller;

import com.wattpilot.common.response.PageResponse;
import com.wattpilot.common.security.AuthenticatedUser;
import com.wattpilot.ev.dto.CreateEvRequest;
import com.wattpilot.ev.dto.EvResponse;
import com.wattpilot.ev.dto.UpdateEvRequest;
import com.wattpilot.ev.entity.EvStatus;
import com.wattpilot.ev.service.EvService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/evs")
@Tag(name = "EV", description = "Manually registered electric vehicles")
public class EvController {

    private final EvService evService;

    public EvController(EvService evService) {
        this.evService = evService;
    }

    @Operation(summary = "Register an EV manually")
    @PostMapping
    public ResponseEntity<EvResponse> createEv(@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
                                               @Valid @RequestBody CreateEvRequest request) {
        EvResponse ev = evService.register(authenticatedUser.userId(), request);
        return ResponseEntity.created(URI.create("/api/v1/evs/" + ev.id())).body(ev);
    }

    @Operation(summary = "List my EVs")
    @GetMapping
    public ResponseEntity<PageResponse<EvResponse>> listEvs(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(name = "status", required = false) EvStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(evService.list(authenticatedUser.userId(), status, pageable));
    }

    @Operation(summary = "Get an EV")
    @GetMapping("/{evId}")
    public ResponseEntity<EvResponse> getEv(@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
                                            @PathVariable Long evId) {
        return ResponseEntity.ok(evService.get(authenticatedUser.userId(), evId));
    }

    @Operation(summary = "Update an EV")
    @PatchMapping("/{evId}")
    public ResponseEntity<EvResponse> updateEv(@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
                                               @PathVariable Long evId,
                                               @Valid @RequestBody UpdateEvRequest request) {
        return ResponseEntity.ok(evService.update(authenticatedUser.userId(), evId, request));
    }

    @Operation(summary = "Deactivate an EV")
    @DeleteMapping("/{evId}")
    public ResponseEntity<Void> deleteEv(@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
                                         @PathVariable Long evId) {
        evService.deactivate(authenticatedUser.userId(), evId);
        return ResponseEntity.noContent().build();
    }
}
