package com.wattpilot.charging.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wattpilot.charging.entity.ChargingPlanStatus;

import java.time.OffsetDateTime;

/**
 * Body returned by {@code POST /charging-plans} with status 422 when the request is valid but no
 * feasible charging window exists.
 *
 * <p>Served as {@code application/problem+json}. It carries the same fields as the shared
 * {@code ErrorResponse} plus the persisted FAILED plan's id and reason, so the client can still show
 * the user why no recommendation was produced and reference the stored attempt.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChargingPlanInfeasibleResponse(
        OffsetDateTime timestamp,
        int status,
        String code,
        String message,
        String path,
        String traceId,
        Long chargingPlanId,
        ChargingPlanStatus planStatus,
        String reasonCode,
        String failureReason
) {
}
