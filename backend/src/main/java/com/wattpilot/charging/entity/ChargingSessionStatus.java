package com.wattpilot.charging.entity;

/**
 * Execution lifecycle of one {@link ChargingSchedule}'s Mock Charging attempt. Maps onto the
 * PostgreSQL {@code charging_session_status} enum declared in V1__init_schema.sql.
 *
 * <p>A schedule that reaches {@link ChargingScheduleStatus#COMPLETED} or {@link ChargingScheduleStatus#FAILED}
 * has exactly one session recording that outcome; a schedule cancelled while still
 * {@link ChargingScheduleStatus#WAITING} never gets one, because cancellation only happens before any
 * execution attempt. {@link #CANCELLED} is therefore not written by V1 (execution-time cancellation is
 * out of scope) but stays a valid schema value for a future version, the same way
 * {@code ChargingPlanStatus#FAILED} does today.
 */
public enum ChargingSessionStatus {
    STARTED,
    COMPLETED,
    FAILED,
    CANCELLED
}
