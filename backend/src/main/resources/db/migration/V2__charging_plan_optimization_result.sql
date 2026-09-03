-- WattPilot V2: charging plan optimization result
-- File: V2__charging_plan_optimization_result.sql
--
-- A charging_plans row records ONE optimization attempt and its outcome
-- (SUCCEEDED or FAILED). The reservation/execution lifecycle stays on
-- charging_schedules and is not touched here.
--
-- No charging plan was ever persisted before this migration, so columns can be
-- tightened to NOT NULL immediately.


-- =========================================================
-- CHARGING PLANS: owning user
-- =========================================================

ALTER TABLE charging_plans
    ADD COLUMN user_id BIGINT;

ALTER TABLE charging_plans
    ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE charging_plans
    ADD CONSTRAINT fk_charging_plans_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE;

CREATE INDEX idx_charging_plans_user_created
    ON charging_plans(user_id, created_at);


-- =========================================================
-- CHARGING PLANS: status becomes SUCCEEDED / FAILED
-- =========================================================
-- Move off the PENDING/SCHEDULED/... enum. Stored as a CHECK-constrained
-- VARCHAR, matching price_area and the project's preference for CHECK strings
-- over new PostgreSQL enum types.

ALTER TABLE charging_plans
    ALTER COLUMN status DROP DEFAULT;

ALTER TABLE charging_plans
    ALTER COLUMN status TYPE VARCHAR(20) USING status::text;

DROP TYPE charging_plan_status;

ALTER TABLE charging_plans
    ADD CONSTRAINT charging_plan_status_valid
        CHECK (status IN ('SUCCEEDED', 'FAILED'));


-- =========================================================
-- CHARGING PLANS: success / failure integrity
-- =========================================================
-- A SUCCEEDED plan carries a complete recommendation and no failure reason.
-- A FAILED plan carries only the failure reason; every success-only field is null.

ALTER TABLE charging_plans
    ADD CONSTRAINT charging_plan_succeeded_fields_present
        CHECK (
            status <> 'SUCCEEDED'
            OR (
                calculated_energy_kwh IS NOT NULL
                AND effective_charging_power_kw IS NOT NULL
                AND estimated_duration_minutes IS NOT NULL
                AND recommended_start_at IS NOT NULL
                AND recommended_end_at IS NOT NULL
                AND expected_energy_kwh IS NOT NULL
                AND estimated_cost_nok IS NOT NULL
                AND baseline_cost_nok IS NOT NULL
                AND expected_savings_nok IS NOT NULL
                AND failure_reason IS NULL
            )
        );

ALTER TABLE charging_plans
    ADD CONSTRAINT charging_plan_failed_fields_absent
        CHECK (
            status <> 'FAILED'
            OR (
                failure_reason IS NOT NULL
                AND calculated_energy_kwh IS NULL
                AND effective_charging_power_kw IS NULL
                AND estimated_duration_minutes IS NULL
                AND recommended_start_at IS NULL
                AND recommended_end_at IS NULL
                AND expected_energy_kwh IS NULL
                AND estimated_cost_nok IS NULL
                AND baseline_cost_nok IS NULL
                AND expected_savings_nok IS NULL
            )
        );


-- =========================================================
-- CHARGING PLAN SLOTS: snapshot the hourly price
-- =========================================================
-- electricity_prices rows are upserted in place on re-import, so the plan slot
-- keeps its own copy of the unit price it was costed with. expected_cost_nok is
-- planned_energy_kwh * price_per_kwh at calculation time.

ALTER TABLE charging_plan_slots
    ADD COLUMN price_per_kwh NUMERIC(12, 6);

ALTER TABLE charging_plan_slots
    ALTER COLUMN price_per_kwh SET NOT NULL;

ALTER TABLE charging_plan_slots
    ADD CONSTRAINT charging_plan_slot_price_non_negative
        CHECK (price_per_kwh >= 0);
