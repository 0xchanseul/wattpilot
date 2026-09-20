-- WattPilot V5: allow negative unit prices on charging plan slots
-- File: V5__allow_negative_plan_slot_price.sql
--
-- Nordic spot prices can be negative when supply exceeds demand. electricity_prices already stores
-- them as-is, and the optimizer correctly picks those hours as the cheapest. The snapshot column on
-- charging_plan_slots must accept the same values, otherwise saving a plan fails for those hours.

ALTER TABLE charging_plan_slots
    DROP CONSTRAINT charging_plan_slot_price_non_negative;
