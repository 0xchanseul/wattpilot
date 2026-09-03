package com.wattpilot.charging.exception;

import com.wattpilot.charging.dto.ChargingPlanInfeasibleResponse;
import com.wattpilot.charging.entity.ChargingPlanStatus;
import com.wattpilot.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Renders a persisted FAILED charging-plan attempt as a 422.
 *
 * <p>Charging-specific so the shared {@code ErrorResponse} contract does not need to grow fields for
 * one endpoint. The body mirrors {@code ErrorResponse} and adds the stored plan id and reason.
 */
@RestControllerAdvice
public class ChargingPlanExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ChargingPlanExceptionHandler.class);

    @ExceptionHandler(ChargingPlanInfeasibleException.class)
    public ResponseEntity<ChargingPlanInfeasibleResponse> handleInfeasible(ChargingPlanInfeasibleException ex,
                                                                           HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.CHARGING_PLAN_INFEASIBLE;
        log.warn("Charging plan infeasible: planId={}, reason={}", ex.chargingPlanId(), ex.reason());
        ChargingPlanInfeasibleResponse body = new ChargingPlanInfeasibleResponse(
                OffsetDateTime.now(ZoneOffset.UTC),
                errorCode.status().value(),
                errorCode.name(),
                errorCode.defaultMessage(),
                request.getRequestURI(),
                newTraceId(),
                ex.chargingPlanId(),
                ChargingPlanStatus.FAILED,
                ex.reason().name(),
                ex.getMessage());
        return ResponseEntity.status(errorCode.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    private static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
