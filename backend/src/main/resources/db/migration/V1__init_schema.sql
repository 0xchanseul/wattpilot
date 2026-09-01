-- WattPilot V1 Initial Schema
-- PostgreSQL / Flyway
-- File: V1__init_schema.sql

-- =========================================================
-- ENUM TYPES
-- =========================================================

CREATE TYPE user_status AS ENUM (
    'ACTIVE',
    'INACTIVE'
);

CREATE TYPE ev_status AS ENUM (
    'ACTIVE',
    'INACTIVE'
);

CREATE TYPE price_provider AS ENUM (
    'HVA_KOSTER_STROMMEN',
    'TIBBER'
);

CREATE TYPE charging_plan_status AS ENUM (
    'PENDING',
    'SCHEDULED',
    'CANCELLED',
    'COMPLETED',
    'FAILED'
);

CREATE TYPE charging_schedule_status AS ENUM (
    'CREATED',
    'WAITING',
    'IN_PROGRESS',
    'COMPLETED',
    'CANCELLED',
    'FAILED'
);

CREATE TYPE charging_session_status AS ENUM (
    'STARTED',
    'COMPLETED',
    'FAILED',
    'CANCELLED'
);


-- =========================================================
-- USERS
-- =========================================================

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    default_price_area VARCHAR(20) NOT NULL,
    status user_status NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT user_default_price_area_valid
        CHECK (default_price_area IN ('NO1', 'NO2', 'NO3', 'NO4', 'NO5'))
);


-- =========================================================
-- REFRESH TOKENS
-- =========================================================

CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT refresh_token_expiry_valid
        CHECK (expires_at > created_at)
);

CREATE INDEX idx_refresh_tokens_user_id
    ON refresh_tokens(user_id);

CREATE INDEX idx_refresh_tokens_expires_at
    ON refresh_tokens(expires_at);


-- =========================================================
-- EVS
-- =========================================================

CREATE TABLE evs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    manufacturer VARCHAR(100) NOT NULL,
    model VARCHAR(100) NOT NULL,
    battery_capacity_kwh NUMERIC(8, 2) NOT NULL,
    max_ac_charging_power_kw NUMERIC(8, 2) NOT NULL,
    default_charger_power_kw NUMERIC(8, 2) NOT NULL,
    status ev_status NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_evs_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT ev_battery_capacity_positive
        CHECK (battery_capacity_kwh > 0),

    CONSTRAINT ev_max_ac_charging_power_valid
        CHECK (
            max_ac_charging_power_kw > 0
            AND max_ac_charging_power_kw <= 22
        ),

    CONSTRAINT ev_default_charger_power_valid
        CHECK (
            default_charger_power_kw > 0
            AND default_charger_power_kw <= 22
        )
);

CREATE INDEX idx_evs_user_id
    ON evs(user_id);


-- =========================================================
-- ELECTRICITY PRICES
-- =========================================================

CREATE TABLE electricity_prices (
    id BIGSERIAL PRIMARY KEY,
    provider price_provider NOT NULL DEFAULT 'HVA_KOSTER_STROMMEN',
    price_area VARCHAR(20) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    price_per_kwh NUMERIC(12, 6) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'NOK',
    fetched_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT electricity_price_time_range_valid
        CHECK (ends_at > starts_at),

    CONSTRAINT electricity_price_area_valid
        CHECK (price_area IN ('NO1', 'NO2', 'NO3', 'NO4', 'NO5')),

    CONSTRAINT uq_electricity_prices_provider_area_start
        UNIQUE (provider, price_area, starts_at)
);

CREATE INDEX idx_electricity_prices_area_start
    ON electricity_prices(price_area, starts_at);


-- =========================================================
-- CHARGING PLANS
-- =========================================================

CREATE TABLE charging_plans (
    id BIGSERIAL PRIMARY KEY,
    ev_id BIGINT NOT NULL,

    current_battery_percent NUMERIC(5, 2) NOT NULL,
    target_battery_percent NUMERIC(5, 2) NOT NULL,
    price_area VARCHAR(20) NOT NULL,
    earliest_start_at TIMESTAMPTZ NOT NULL,
    required_completion_at TIMESTAMPTZ NOT NULL,

    -- EV snapshot used by the optimizer so historical plans remain reproducible
    ev_name VARCHAR(100) NOT NULL,
    ev_manufacturer VARCHAR(100) NOT NULL,
    ev_model VARCHAR(100) NOT NULL,
    battery_capacity_kwh NUMERIC(8, 2) NOT NULL,
    max_ac_charging_power_kw NUMERIC(8, 2) NOT NULL,
    default_charger_power_kw NUMERIC(8, 2) NOT NULL,

    calculated_energy_kwh NUMERIC(8, 2),
    effective_charging_power_kw NUMERIC(8, 2),
    estimated_duration_minutes INTEGER,

    recommended_start_at TIMESTAMPTZ,
    recommended_end_at TIMESTAMPTZ,
    expected_energy_kwh NUMERIC(8, 2),
    estimated_cost_nok NUMERIC(12, 4),
    baseline_cost_nok NUMERIC(12, 4),
    expected_savings_nok NUMERIC(12, 4),

    status charging_plan_status NOT NULL DEFAULT 'PENDING',
    failure_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_charging_plans_ev
        FOREIGN KEY (ev_id)
        REFERENCES evs(id)
        ON DELETE CASCADE,

    CONSTRAINT charging_plan_current_battery_valid
        CHECK (
            current_battery_percent >= 0
            AND current_battery_percent <= 100
        ),

    CONSTRAINT charging_plan_target_battery_valid
        CHECK (
            target_battery_percent > 0
            AND target_battery_percent <= 100
        ),

    CONSTRAINT charging_plan_battery_range_valid
        CHECK (target_battery_percent > current_battery_percent),

    CONSTRAINT charging_plan_price_area_valid
        CHECK (price_area IN ('NO1', 'NO2', 'NO3', 'NO4', 'NO5')),

    CONSTRAINT charging_plan_window_valid
        CHECK (required_completion_at > earliest_start_at),

    CONSTRAINT charging_plan_battery_capacity_positive
        CHECK (battery_capacity_kwh > 0),

    CONSTRAINT charging_plan_max_ac_power_positive
        CHECK (max_ac_charging_power_kw > 0),

    CONSTRAINT charging_plan_charger_power_positive
        CHECK (default_charger_power_kw > 0),

    CONSTRAINT charging_plan_energy_non_negative
        CHECK (
            calculated_energy_kwh IS NULL
            OR calculated_energy_kwh >= 0
        ),

    CONSTRAINT charging_plan_effective_power_positive
        CHECK (
            effective_charging_power_kw IS NULL
            OR effective_charging_power_kw > 0
        ),

    CONSTRAINT charging_plan_duration_positive
        CHECK (
            estimated_duration_minutes IS NULL
            OR estimated_duration_minutes > 0
        ),

    CONSTRAINT charging_plan_recommended_range_valid
        CHECK (
            recommended_end_at IS NULL
            OR recommended_start_at IS NULL
            OR recommended_end_at > recommended_start_at
        ),

    CONSTRAINT charging_plan_expected_energy_non_negative
        CHECK (
            expected_energy_kwh IS NULL
            OR expected_energy_kwh >= 0
        )
);

CREATE INDEX idx_charging_plans_ev_id
    ON charging_plans(ev_id);

CREATE INDEX idx_charging_plans_status_completion
    ON charging_plans(status, required_completion_at);


-- =========================================================
-- CHARGING PLAN SLOTS
-- =========================================================

CREATE TABLE charging_plan_slots (
    id BIGSERIAL PRIMARY KEY,
    charging_plan_id BIGINT NOT NULL,
    electricity_price_id BIGINT NOT NULL,

    slot_start_at TIMESTAMPTZ NOT NULL,
    slot_end_at TIMESTAMPTZ NOT NULL,
    planned_energy_kwh NUMERIC(8, 2) NOT NULL,
    expected_cost_nok NUMERIC(12, 4) NOT NULL,
    sequence_no INTEGER NOT NULL,

    CONSTRAINT fk_charging_plan_slots_plan
        FOREIGN KEY (charging_plan_id)
        REFERENCES charging_plans(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_charging_plan_slots_price
        FOREIGN KEY (electricity_price_id)
        REFERENCES electricity_prices(id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_charging_plan_slots_sequence
        UNIQUE (charging_plan_id, sequence_no),

    CONSTRAINT charging_plan_slot_time_range_valid
        CHECK (slot_end_at > slot_start_at),

    CONSTRAINT charging_plan_slot_energy_positive
        CHECK (planned_energy_kwh > 0),

    CONSTRAINT charging_plan_slot_sequence_positive
        CHECK (sequence_no > 0)
);

CREATE INDEX idx_charging_plan_slots_plan_id
    ON charging_plan_slots(charging_plan_id);


-- =========================================================
-- CHARGING SCHEDULES
-- =========================================================

CREATE TABLE charging_schedules (
    id BIGSERIAL PRIMARY KEY,
    charging_plan_id BIGINT NOT NULL,

    scheduled_start_at TIMESTAMPTZ NOT NULL,
    scheduled_end_at TIMESTAMPTZ NOT NULL,
    expected_energy_kwh NUMERIC(8, 2) NOT NULL,
    estimated_cost_nok NUMERIC(12, 4) NOT NULL,

    status charging_schedule_status NOT NULL DEFAULT 'CREATED',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_charging_schedules_plan
        FOREIGN KEY (charging_plan_id)
        REFERENCES charging_plans(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_charging_schedules_plan
        UNIQUE (charging_plan_id),

    CONSTRAINT charging_schedule_time_range_valid
        CHECK (scheduled_end_at > scheduled_start_at),

    CONSTRAINT charging_schedule_energy_positive
        CHECK (expected_energy_kwh > 0)
);

CREATE INDEX idx_charging_schedules_status_start
    ON charging_schedules(status, scheduled_start_at);


-- =========================================================
-- CHARGING SCHEDULE SLOTS
-- =========================================================

CREATE TABLE charging_schedule_slots (
    id BIGSERIAL PRIMARY KEY,
    charging_schedule_id BIGINT NOT NULL,
    electricity_price_id BIGINT NOT NULL,

    slot_start_at TIMESTAMPTZ NOT NULL,
    slot_end_at TIMESTAMPTZ NOT NULL,
    planned_energy_kwh NUMERIC(8, 2) NOT NULL,
    expected_cost_nok NUMERIC(12, 4) NOT NULL,
    sequence_no INTEGER NOT NULL,

    CONSTRAINT fk_charging_schedule_slots_schedule
        FOREIGN KEY (charging_schedule_id)
        REFERENCES charging_schedules(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_charging_schedule_slots_price
        FOREIGN KEY (electricity_price_id)
        REFERENCES electricity_prices(id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_charging_schedule_slots_sequence
        UNIQUE (charging_schedule_id, sequence_no),

    CONSTRAINT charging_schedule_slot_time_range_valid
        CHECK (slot_end_at > slot_start_at),

    CONSTRAINT charging_schedule_slot_energy_positive
        CHECK (planned_energy_kwh > 0),

    CONSTRAINT charging_schedule_slot_sequence_positive
        CHECK (sequence_no > 0)
);

CREATE INDEX idx_charging_schedule_slots_schedule_id
    ON charging_schedule_slots(charging_schedule_id);


-- =========================================================
-- CHARGING SESSIONS
-- =========================================================

CREATE TABLE charging_sessions (
    id BIGSERIAL PRIMARY KEY,
    charging_schedule_id BIGINT NOT NULL,

    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    actual_energy_kwh NUMERIC(8, 2),
    actual_cost_nok NUMERIC(12, 4),

    baseline_cost_nok NUMERIC(12, 4),
    optimized_cost_nok NUMERIC(12, 4),
    estimated_savings_nok NUMERIC(12, 4),

    status charging_session_status NOT NULL,
    failure_reason VARCHAR(500),

    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_charging_sessions_schedule
        FOREIGN KEY (charging_schedule_id)
        REFERENCES charging_schedules(id)
        ON DELETE CASCADE,

    CONSTRAINT charging_session_time_range_valid
        CHECK (
            completed_at IS NULL
            OR started_at IS NULL
            OR completed_at >= started_at
        ),

    CONSTRAINT charging_session_energy_non_negative
        CHECK (
            actual_energy_kwh IS NULL
            OR actual_energy_kwh >= 0
        )
);

CREATE INDEX idx_charging_sessions_schedule_id
    ON charging_sessions(charging_schedule_id);

CREATE INDEX idx_charging_sessions_started_at
    ON charging_sessions(started_at);
