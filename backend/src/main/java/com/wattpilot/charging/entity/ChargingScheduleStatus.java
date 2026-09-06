package com.wattpilot.charging.entity;

/**
 * Reservation and execution lifecycle of a charging schedule. Maps onto the PostgreSQL
 * {@code charging_schedule_status} enum declared in V1__init_schema.sql and narrowed in
 * V3__remove_created_schedule_status_and_add_retry.sql.
 *
 * <p>A confirmed schedule is created directly in {@link #WAITING}. {@link #WAITING} and
 * {@link #IN_PROGRESS} count as active for the overlap check that stops an EV being double-booked.
 */
public enum ChargingScheduleStatus {
    WAITING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    FAILED
}
