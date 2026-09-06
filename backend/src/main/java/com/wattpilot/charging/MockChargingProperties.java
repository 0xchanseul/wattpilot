package com.wattpilot.charging;

import com.wattpilot.charging.entity.ChargingFailureCode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Demo/test-only failure injection for {@code MockChargingAdapter}. Empty by default, so mock charging
 * always succeeds unless a deployment explicitly opts in — the same "off unless asked" stance as the
 * scheduler's own {@code enabled} flag.
 *
 * <p>{@code failures} maps a {@code charging_schedules} id to the outcome the adapter should produce
 * for it instead of success:
 * <ul>
 *   <li>{@code CHARGER_UNAVAILABLE} / {@code VEHICLE_DISCONNECTED} / {@code START_REJECTED} — a
 *       business failure returned from the <b>start</b> phase; the schedule ends {@code FAILED}
 *       immediately, with no retry.</li>
 *   <li>{@code CHARGING_INTERRUPTED} — a business failure returned from the <b>completion</b> phase,
 *       so the schedule reaches {@code IN_PROGRESS} first and then fails.</li>
 *   <li>{@code SYSTEM_ERROR} — the adapter <b>throws</b> on the start phase, so
 *       {@code ChargingExecutionService} runs its bounded retry and finalizes the schedule as
 *       {@code FAILED(SYSTEM_ERROR)} once the budget is exhausted.</li>
 * </ul>
 *
 * <p>{@code MISSED_EXECUTION_WINDOW} is rejected here: it describes a schedule that was never executed
 * at all, which the adapter has no part in producing.
 *
 * @param failures charging-schedule id → the failure the adapter should simulate for it
 */
@ConfigurationProperties("wattpilot.charging.execution.mock")
public record MockChargingProperties(Map<Long, ChargingFailureCode> failures) {

    public MockChargingProperties(Map<Long, ChargingFailureCode> failures) {
        Map<Long, ChargingFailureCode> copy = failures == null ? Map.of() : Map.copyOf(failures);
        copy.forEach((scheduleId, code) -> {
            if (code == ChargingFailureCode.MISSED_EXECUTION_WINDOW) {
                throw new IllegalArgumentException(
                        "MISSED_EXECUTION_WINDOW cannot be injected via wattpilot.charging.execution.mock.failures "
                                + "(schedule id=" + scheduleId + "); it is not a Mock Charging adapter outcome");
            }
        });
        this.failures = copy;
    }
}
