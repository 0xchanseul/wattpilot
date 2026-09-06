-- WattPilot V3: drop the unused CREATED schedule status, add bounded-retry bookkeeping
-- File: V3__remove_created_schedule_status_and_add_retry.sql
--
-- A confirmed schedule has always been created directly in WAITING in practice; CREATED never carried
-- distinct meaning and the application no longer writes it. PostgreSQL cannot drop a single value from
-- an enum type, so the type is recreated without it.


-- =========================================================
-- CHARGING SCHEDULES: drop CREATED from charging_schedule_status
-- =========================================================

-- Defensive: no row should be CREATED (the application always wrote WAITING or later), but normalise
-- before narrowing the type so the migration is safe even against manually inserted data.
UPDATE charging_schedules SET status = 'WAITING' WHERE status = 'CREATED';

ALTER TABLE charging_schedules ALTER COLUMN status DROP DEFAULT;

ALTER TYPE charging_schedule_status RENAME TO charging_schedule_status_old;

CREATE TYPE charging_schedule_status AS ENUM (
    'WAITING',
    'IN_PROGRESS',
    'COMPLETED',
    'CANCELLED',
    'FAILED'
);

ALTER TABLE charging_schedules
    ALTER COLUMN status TYPE charging_schedule_status
    USING status::text::charging_schedule_status;

ALTER TABLE charging_schedules
    ALTER COLUMN status SET DEFAULT 'WAITING';

DROP TYPE charging_schedule_status_old;


-- =========================================================
-- CHARGING SCHEDULES: bounded retry bookkeeping for transient execution errors
-- =========================================================
-- retry_count/next_retry_at track ChargingExecutionService's bounded backoff for errors thrown by
-- ChargingExecutionPort. A database failure during an execution attempt is a different case: the whole
-- attempt rolls back and is retried for free on the next scheduler tick, so it never touches these
-- columns.

ALTER TABLE charging_schedules ADD COLUMN retry_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE charging_schedules ADD COLUMN next_retry_at TIMESTAMPTZ;

ALTER TABLE charging_schedules
    ADD CONSTRAINT charging_schedule_retry_count_non_negative
        CHECK (retry_count >= 0);

ALTER TABLE charging_schedules
    ADD CONSTRAINT charging_schedule_next_retry_only_when_active
        CHECK (next_retry_at IS NULL OR status IN ('WAITING', 'IN_PROGRESS'));


-- =========================================================
-- CHARGING SCHEDULES: index for the completion scan
-- =========================================================
-- idx_charging_schedules_status_start (status, scheduled_start_at) from V1 already serves the
-- WAITING start-ready and missed scans. The completion scan filters on IN_PROGRESS + scheduled_end_at,
-- which that index cannot serve efficiently.

CREATE INDEX idx_charging_schedules_in_progress_end
    ON charging_schedules(scheduled_end_at)
    WHERE status = 'IN_PROGRESS';
