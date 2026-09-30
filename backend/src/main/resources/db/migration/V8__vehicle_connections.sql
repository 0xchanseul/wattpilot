-- Read-only link between a WattPilot EV and a Smartcar vehicle (V1.5, see docs/mvp-scope.md).
-- Smartcar API v3 uses one application-level access token (no per-vehicle OAuth tokens), so no
-- secret is stored here: only the ids needed to scope Smartcar requests to this vehicle.
CREATE TABLE vehicle_connections (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    ev_id BIGINT NOT NULL,
    smartcar_user_id VARCHAR(64) NOT NULL,
    smartcar_vehicle_id VARCHAR(64) NOT NULL,
    smartcar_connection_id VARCHAR(64) NOT NULL,
    vehicle_make VARCHAR(100),
    vehicle_model VARCHAR(100),
    vehicle_year INTEGER,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_vehicle_connections_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_vehicle_connections_ev FOREIGN KEY (ev_id) REFERENCES evs(id) ON DELETE CASCADE,
    CONSTRAINT uq_vehicle_connections_ev UNIQUE (ev_id),
    CONSTRAINT uq_vehicle_connections_user_vehicle UNIQUE (user_id, smartcar_vehicle_id)
);

CREATE INDEX idx_vehicle_connections_user_id ON vehicle_connections(user_id);
