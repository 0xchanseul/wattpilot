package com.wattpilot.charging.repository;

import com.wattpilot.charging.entity.ChargingPlan;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Copies finished charging history between accounts for the demo login.
 *
 * <p>Native SQL on purpose: a copy must keep every column exactly as it is, including
 * {@code created_at} and {@code updated_at}, which the history read model orders by. Going through the
 * entities would stamp the copies with the time of the copy instead. The new plan and schedule ids are
 * taken from their sequences up front, so one statement can link plan, slots, schedule and session
 * together without reading anything back.
 */
public interface DemoChargingHistoryRepository extends Repository<ChargingPlan, Long> {

    /**
     * Copies the charging history of one template EV onto one visitor EV: every plan whose schedule has
     * a {@code COMPLETED} or {@code FAILED} session, with its slots, schedule and session. Cancelled,
     * waiting and running schedules are left out, so the copy holds nothing the execution scheduler
     * would still act on. {@code charging_schedule_slots} is unused in V1 and not copied.
     */
    @Modifying
    @Query(value = """
            WITH src AS (
                SELECT p.id AS old_plan_id,
                       nextval(pg_get_serial_sequence('charging_plans', 'id')) AS new_plan_id,
                       s.id AS old_schedule_id,
                       nextval(pg_get_serial_sequence('charging_schedules', 'id')) AS new_schedule_id,
                       ss.id AS old_session_id
                FROM charging_plans p
                JOIN charging_schedules s ON s.charging_plan_id = p.id
                JOIN charging_sessions ss ON ss.charging_schedule_id = s.id
                WHERE p.user_id = :templateUserId
                  AND p.ev_id = :templateEvId
                  AND ss.status IN ('COMPLETED', 'FAILED')
            ),
            new_plans AS (
                INSERT INTO charging_plans (id, user_id, ev_id, current_battery_percent, target_battery_percent,
                    price_area, earliest_start_at, required_completion_at, ev_name, ev_manufacturer, ev_model,
                    battery_capacity_kwh, max_ac_charging_power_kw, default_charger_power_kw, calculated_energy_kwh,
                    effective_charging_power_kw, estimated_duration_minutes, recommended_start_at,
                    recommended_end_at, expected_energy_kwh, estimated_cost_nok, baseline_cost_nok,
                    expected_savings_nok, status, failure_reason, created_at, updated_at)
                SELECT src.new_plan_id, :visitorUserId, :visitorEvId, p.current_battery_percent,
                    p.target_battery_percent, p.price_area, p.earliest_start_at, p.required_completion_at,
                    p.ev_name, p.ev_manufacturer, p.ev_model, p.battery_capacity_kwh, p.max_ac_charging_power_kw,
                    p.default_charger_power_kw, p.calculated_energy_kwh, p.effective_charging_power_kw,
                    p.estimated_duration_minutes, p.recommended_start_at, p.recommended_end_at,
                    p.expected_energy_kwh, p.estimated_cost_nok, p.baseline_cost_nok, p.expected_savings_nok,
                    p.status, p.failure_reason, p.created_at, p.updated_at
                FROM src
                JOIN charging_plans p ON p.id = src.old_plan_id
            ),
            new_plan_slots AS (
                INSERT INTO charging_plan_slots (charging_plan_id, electricity_price_id, slot_start_at,
                    slot_end_at, price_per_kwh, planned_energy_kwh, expected_cost_nok, sequence_no)
                SELECT src.new_plan_id, sl.electricity_price_id, sl.slot_start_at, sl.slot_end_at,
                    sl.price_per_kwh, sl.planned_energy_kwh, sl.expected_cost_nok, sl.sequence_no
                FROM src
                JOIN charging_plan_slots sl ON sl.charging_plan_id = src.old_plan_id
            ),
            new_schedules AS (
                INSERT INTO charging_schedules (id, charging_plan_id, scheduled_start_at, scheduled_end_at,
                    expected_energy_kwh, estimated_cost_nok, status, retry_count, next_retry_at, created_at,
                    updated_at)
                SELECT src.new_schedule_id, src.new_plan_id, s.scheduled_start_at, s.scheduled_end_at,
                    s.expected_energy_kwh, s.estimated_cost_nok, s.status, s.retry_count, s.next_retry_at,
                    s.created_at, s.updated_at
                FROM src
                JOIN charging_schedules s ON s.id = src.old_schedule_id
            )
            INSERT INTO charging_sessions (charging_schedule_id, started_at, completed_at, actual_energy_kwh,
                actual_cost_nok, baseline_cost_nok, optimized_cost_nok, estimated_savings_nok, status,
                failure_code, failure_reason, created_at, updated_at)
            SELECT src.new_schedule_id, ss.started_at, ss.completed_at, ss.actual_energy_kwh, ss.actual_cost_nok,
                ss.baseline_cost_nok, ss.optimized_cost_nok, ss.estimated_savings_nok, ss.status, ss.failure_code,
                ss.failure_reason, ss.created_at, ss.updated_at
            FROM src
            JOIN charging_sessions ss ON ss.id = src.old_session_id
            """, nativeQuery = true)
    int copyFinishedHistory(@Param("templateUserId") Long templateUserId,
                            @Param("templateEvId") Long templateEvId,
                            @Param("visitorUserId") Long visitorUserId,
                            @Param("visitorEvId") Long visitorEvId);
}
