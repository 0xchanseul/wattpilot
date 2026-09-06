package com.wattpilot.charging;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Settings for the charging-execution scheduler and its bounded retry policy for transient technical
 * errors. A database failure that rolls back an entire attempt is retried for free on the next tick
 * (nothing was committed, so nothing counts against the budget below) — these settings only govern
 * errors thrown by {@code ChargingExecutionPort} itself; see {@code ChargingExecutionService}.
 *
 * @param enabled                whether the scheduler runs; disabled for local development and tests
 * @param cron                   when a tick fires; the default is every minute on the minute
 * @param batchSize              maximum schedules processed per phase (start / complete / missed) per tick
 * @param retryMaxAttempts       attempts allowed for one phase before it is finalized as FAILED(SYSTEM_ERROR)
 * @param retryInitialBackoff    delay before the first retry
 * @param retryBackoffMultiplier factor applied to the delay after each further retry
 */
@ConfigurationProperties("wattpilot.charging.execution")
public record ChargingExecutionProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("0 * * * * *") String cron,
        @DefaultValue("100") int batchSize,
        @DefaultValue("3") int retryMaxAttempts,
        @DefaultValue("PT1M") Duration retryInitialBackoff,
        @DefaultValue("2.0") double retryBackoffMultiplier
) {
}
