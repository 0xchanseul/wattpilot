-- =========================================================
-- VEHICLE MODELS (V1.5 master data)
-- =========================================================
-- Manually curated presets used only to prefill the EV registration form (manufacturer, model,
-- battery capacity, max AC charging power). No foreign key from evs: editing or re-seeding a preset
-- never touches an already-registered EV. default_charger_power_kw is intentionally not stored here
-- - it describes the user's own home charger, not a vehicle spec.

CREATE TABLE vehicle_models (
    id BIGSERIAL PRIMARY KEY,
    manufacturer VARCHAR(100) NOT NULL,
    model VARCHAR(100) NOT NULL,
    battery_capacity_kwh NUMERIC(8, 2) NOT NULL,
    max_ac_charging_power_kw NUMERIC(8, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_vehicle_models_manufacturer_model
        UNIQUE (manufacturer, model),

    CONSTRAINT vehicle_model_battery_capacity_positive
        CHECK (battery_capacity_kwh > 0),

    CONSTRAINT vehicle_model_max_ac_charging_power_valid
        CHECK (
            max_ac_charging_power_kw > 0
            AND max_ac_charging_power_kw <= 22
        )
);

-- Representative EV models for the Norwegian market (10-20 presets per docs/mvp-scope.md V1.5 scope).
-- Battery capacity is usable capacity; max AC charging power is the vehicle's onboard AC charger limit
-- (not DC fast-charging power). Figures are the most common European trim as of the 2026 model lineup
-- and are approximate where a model offers multiple onboard-charger options across trims.
INSERT INTO vehicle_models (manufacturer, model, battery_capacity_kwh, max_ac_charging_power_kw) VALUES
    ('Tesla', 'Model Y Long Range', 75.00, 11.00),
    ('Tesla', 'Model 3 Long Range', 75.00, 11.00),
    ('Volkswagen', 'ID.4 Pro', 77.00, 11.00),
    ('Volkswagen', 'ID.3 Pro', 58.00, 11.00),
    ('Skoda', 'Enyaq 80', 77.00, 11.00),
    ('Audi', 'Q4 e-tron 45', 77.00, 11.00),
    ('BMW', 'i4 eDrive40', 81.00, 11.00),
    ('Hyundai', 'Ioniq 5 Long Range', 77.40, 11.00),
    ('Kia', 'EV6 Long Range', 77.40, 11.00),
    ('Kia', 'Niro EV', 64.80, 7.20),
    ('Nissan', 'Leaf e+', 59.00, 6.60),
    ('Nissan', 'Ariya 63kWh', 63.00, 7.40),
    ('Polestar', '2 Long Range Single Motor', 79.00, 11.00),
    ('Volvo', 'EX30 Single Motor Extended Range', 64.00, 11.00),
    ('Volvo', 'EX40 Single Motor Extended Range', 79.00, 11.00),
    ('Cupra', 'Born 58 kWh', 58.00, 11.00),
    ('MG', 'MG4 Standard Range', 51.00, 6.60),
    ('Renault', 'Megane E-Tech EV60', 60.00, 22.00),
    ('Toyota', 'bZ4X FWD', 63.40, 6.60),
    ('BYD', 'Atto 3 Extended Range', 60.48, 7.00);
