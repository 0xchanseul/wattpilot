-- WattPilot V4: charging_sessions execution-result integrity
-- File: V4__charging_session_execution_fields.sql
--
-- charging_sessions has existed since V1 but was never populated (no Mock Charging execution service
-- existed yet). This migration adds the machine-readable failure_code the execution service relies on,
-- enforces exactly one session per schedule, and adds per-status field-presence CHECK constraints
-- mirroring the pattern charging_plans already uses (see V2__charging_plan_optimization_result.sql).
-- charging_plans' own constraints are untouched: that table records the optimizer's recommendation,
-- this one records what actually happened when the schedule was executed.


-- =========================================================
-- CHARGING SESSIONS: machine-readable failure reason
-- =========================================================

ALTER TABLE charging_sessions ADD COLUMN failure_code VARCHAR(50);

ALTER TABLE charging_sessions
    ADD CONSTRAINT charging_session_failure_code_valid
        CHECK (
            failure_code IS NULL OR failure_code IN (
                'CHARGER_UNAVAILABLE',
                'VEHICLE_DISCONNECTED',
                'START_REJECTED',
                'CHARGING_INTERRUPTED',
                'MISSED_EXECUTION_WINDOW',
                'SYSTEM_ERROR'
            )
        );


-- =========================================================
-- CHARGING SESSIONS: exactly one session per schedule
-- =========================================================

ALTER TABLE charging_sessions
    ADD CONSTRAINT uq_charging_sessions_schedule UNIQUE (charging_schedule_id);


-- =========================================================
-- CHARGING SESSIONS: per-status field presence
-- =========================================================
-- STARTED   - only startedAt is known; nothing about the outcome is yet.
-- COMPLETED - the full realized outcome; no failure fields.
-- FAILED    - only the failure reason; startedAt may or may not be set (null if the window closed or
--             the start attempt itself failed before charging ever began; set if a started charge later
--             failed to complete), but no outcome figures were realized.
-- CANCELLED - not written by V1 (execution-time cancellation is out of scope; a schedule cancelled
--             while still WAITING never gets a session at all), but kept valid for a future version.

ALTER TABLE charging_sessions
    ADD CONSTRAINT charging_session_started_fields_valid
        CHECK (
            status <> 'STARTED' OR (
                started_at IS NOT NULL
                AND completed_at IS NULL
                AND actual_energy_kwh IS NULL AND actual_cost_nok IS NULL
                AND baseline_cost_nok IS NULL AND optimized_cost_nok IS NULL AND estimated_savings_nok IS NULL
                AND failure_code IS NULL AND failure_reason IS NULL
            )
        );

ALTER TABLE charging_sessions
    ADD CONSTRAINT charging_session_completed_fields_valid
        CHECK (
            status <> 'COMPLETED' OR (
                started_at IS NOT NULL AND completed_at IS NOT NULL AND completed_at >= started_at
                AND actual_energy_kwh IS NOT NULL AND actual_cost_nok IS NOT NULL
                AND baseline_cost_nok IS NOT NULL AND optimized_cost_nok IS NOT NULL AND estimated_savings_nok IS NOT NULL
                AND failure_code IS NULL AND failure_reason IS NULL
            )
        );

ALTER TABLE charging_sessions
    ADD CONSTRAINT charging_session_failed_fields_valid
        CHECK (
            status <> 'FAILED' OR (
                completed_at IS NULL
                AND actual_energy_kwh IS NULL AND actual_cost_nok IS NULL
                AND baseline_cost_nok IS NULL AND optimized_cost_nok IS NULL AND estimated_savings_nok IS NULL
                AND failure_code IS NOT NULL AND failure_reason IS NOT NULL
            )
        );

ALTER TABLE charging_sessions
    ADD CONSTRAINT charging_session_cancelled_fields_valid
        CHECK (
            status <> 'CANCELLED' OR (
                completed_at IS NULL
                AND actual_energy_kwh IS NULL AND actual_cost_nok IS NULL
                AND baseline_cost_nok IS NULL AND optimized_cost_nok IS NULL AND estimated_savings_nok IS NULL
                AND failure_code IS NULL AND failure_reason IS NULL
            )
        );
