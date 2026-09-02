package com.wattpilot.electricity.entity;

/**
 * Source of an electricity price row. Maps onto the PostgreSQL {@code price_provider} enum declared
 * in V1__init_schema.sql.
 *
 * <p>V1 imports prices only from Hva koster strømmen. {@code TIBBER} exists so the enum matches the
 * database type; it is not populated until the V1.5 Tibber integration is built.
 */
public enum PriceProvider {
    HVA_KOSTER_STROMMEN,
    TIBBER
}
