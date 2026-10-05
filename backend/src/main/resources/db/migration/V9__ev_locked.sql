-- A locked EV cannot be edited, deactivated or have its vehicle connection removed through the API.
-- Used by the shared demo account so visitors cannot break its fixed demo vehicles. There is no API
-- to set the flag; it is set directly in the database.
ALTER TABLE evs ADD COLUMN locked BOOLEAN NOT NULL DEFAULT FALSE;
