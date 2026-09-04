package com.wattpilot.charging.entity;

/**
 * Reservation and execution lifecycle of a charging schedule. Maps onto the PostgreSQL
 * {@code charging_schedule_status} enum declared in V1__init_schema.sql.
 *
 * <p>V1 only creates schedules in {@link #CREATED}; the later states belong to the mock-charging and
 * execution steps. {@link #CREATED}, {@link #WAITING} and {@link #IN_PROGRESS} count as active for the
 * overlap check that stops an EV being double-booked.
 */
public enum ChargingScheduleStatus {
    CREATED,
    WAITING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    FAILED
}
