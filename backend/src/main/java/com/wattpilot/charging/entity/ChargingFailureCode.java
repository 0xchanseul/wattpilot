package com.wattpilot.charging.entity;

/**
 * Machine-readable reason a {@link ChargingSession} ended in {@link ChargingSessionStatus#FAILED}.
 * Stored as a CHECK-constrained {@code VARCHAR}, not a database enum type, matching
 * {@code charging_plans.status}.
 *
 * <p>The first five values are execution failures the {@code ChargingExecutionPort} reports directly.
 * {@link #SYSTEM_ERROR} is used only after transient technical errors exhaust their retry budget; it
 * never carries internal exception detail, which stays in the server log.
 */
public enum ChargingFailureCode {
    CHARGER_UNAVAILABLE,
    VEHICLE_DISCONNECTED,
    START_REJECTED,
    CHARGING_INTERRUPTED,
    MISSED_EXECUTION_WINDOW,
    SYSTEM_ERROR
}
