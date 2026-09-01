package com.wattpilot.ev.entity;

/**
 * Whether an EV is in active use. Deactivating an EV keeps its charging history but hides it from
 * the default listing; it can be reactivated later.
 */
public enum EvStatus {
    ACTIVE,
    INACTIVE
}
