package com.wattpilot.charging.exception;

import com.wattpilot.charging.dto.OptimizationResult;

/**
 * Raised after a FAILED charging-plan attempt has been persisted, to drive the 422 response.
 *
 * <p>The transaction that persists the FAILED plan must NOT roll back on this exception (see
 * {@code ChargingPlanService}), so the attempt stays queryable for record-keeping even though the
 * caller receives an error.
 */
public class ChargingPlanInfeasibleException extends RuntimeException {

    private final Long chargingPlanId;
    private final OptimizationResult.Reason reason;

    public ChargingPlanInfeasibleException(Long chargingPlanId, OptimizationResult.Reason reason, String detail) {
        super(detail);
        this.chargingPlanId = chargingPlanId;
        this.reason = reason;
    }

    public Long chargingPlanId() {
        return chargingPlanId;
    }

    public OptimizationResult.Reason reason() {
        return reason;
    }
}
