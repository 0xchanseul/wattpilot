package com.wattpilot.common;

/**
 * Norwegian electricity price areas (NO1-NO5).
 *
 * <p>Shared across modules: a user has a default area, and price lookups and charging
 * plans each take an explicit area.
 */
public enum PriceArea {
    NO1,
    NO2,
    NO3,
    NO4,
    NO5
}
