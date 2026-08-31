package com.wattpilot.user.entity;

/**
 * Whether an account can still be used. A deactivated account keeps its history but is
 * refused at login.
 */
public enum UserStatus {
    ACTIVE,
    INACTIVE
}
