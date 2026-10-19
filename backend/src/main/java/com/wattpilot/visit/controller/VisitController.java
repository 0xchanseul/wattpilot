package com.wattpilot.visit.controller;

import com.wattpilot.visit.dto.VisitRequest;
import com.wattpilot.visit.service.VisitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/visits")
@Tag(name = "Visits", description = "Visitor counting")
public class VisitController {

    // Set by nginx to the connecting address. X-Forwarded-For is not used because it keeps whatever
    // chain the client sent, so its first entry can be forged.
    private static final String REAL_IP_HEADER = "X-Real-IP";

    private final VisitService visitService;

    public VisitController(VisitService visitService) {
        this.visitService = visitService;
    }

    @Operation(summary = "Record a visit")
    @PostMapping
    public ResponseEntity<Void> recordVisit(HttpServletRequest request,
                                            @RequestBody(required = false) VisitRequest body) {
        String realIp = request.getHeader(REAL_IP_HEADER);
        String clientIp = realIp != null && !realIp.isBlank() ? realIp : request.getRemoteAddr();
        visitService.recordVisit(
                clientIp,
                request.getHeader(HttpHeaders.USER_AGENT),
                body != null ? body.referrer() : null,
                body != null ? body.utmSource() : null);
        return ResponseEntity.noContent().build();
    }
}
