package com.wattpilot.charging.entity;

/**
 * Outcome of a single charging-plan optimization attempt.
 *
 * <p>This is not a reservation or execution state: that lifecycle is tracked separately on
 * {@code charging_schedules}. Stored as a CHECK-constrained {@code VARCHAR} column, not a database
 * enum type.
 */
public enum ChargingPlanStatus {
    /** A feasible continuous charging window was found; the recommendation fields are populated. */
    SUCCEEDED,
    /** The request was valid but no feasible window could be produced; only {@code failureReason} is set. */
    FAILED
}
