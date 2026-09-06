package com.wattpilot.charging.port;

/**
 * Boundary between the charging domain and whatever actually starts and stops a charge. V1's only
 * implementation is {@code MockChargingAdapter}; a real vehicle/charger integration can implement the
 * same interface without {@code ChargingExecutionService} changing.
 *
 * <p>A normal business outcome — the charger or vehicle accepted or rejected the command — comes back
 * as an {@link ExecutionOutcome}. A method may instead throw an unchecked exception to signal a
 * transient, technical failure to reach the underlying system; the caller treats that as retryable
 * with a bounded backoff rather than as a definitive result.
 */
public interface ChargingExecutionPort {

    ExecutionOutcome start(Long scheduleId);

    ExecutionOutcome complete(Long scheduleId);
}
