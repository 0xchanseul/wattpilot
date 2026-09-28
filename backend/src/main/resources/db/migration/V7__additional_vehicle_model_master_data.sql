-- =========================================================
-- VEHICLE MODELS (V1.5 master data) - second batch
-- =========================================================
-- 10 additional presets, picked from actual Norwegian EV sales rankings (elbilstatistikk.no top-20,
-- September 2026 snapshot) rather than general representativeness, to cover recent nameplates not in
-- the V6 seed. Same conventions as V6: battery capacity is usable capacity, max AC charging power is
-- the vehicle's onboard AC charger limit (not DC fast-charging power), and figures are approximate
-- where a model offers multiple onboard-charger options across trims.
INSERT INTO vehicle_models (manufacturer, model, battery_capacity_kwh, max_ac_charging_power_kw) VALUES
    ('Volkswagen', 'ID.Buzz Pro', 79.00, 11.00),
    ('Toyota', 'Urban Cruiser', 61.10, 11.00),
    ('BMW', 'iX3 50 xDrive', 108.70, 11.00),
    ('Kia', 'EV5', 81.40, 11.00),
    ('Volkswagen', 'ID.7 Pro', 77.00, 11.00),
    ('Smart', '#5 Pro', 74.40, 22.00),
    ('Xpeng', 'G6 Long Range', 80.80, 11.00),
    ('Ford', 'Explorer Extended Range RWD', 77.00, 11.00),
    ('BYD', 'Sealion 7 Comfort', 82.50, 11.00),
    ('Toyota', 'C-HR+ 77 kWh', 77.00, 11.00);
