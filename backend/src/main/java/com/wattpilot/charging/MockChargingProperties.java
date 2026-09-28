package com.wattpilot.charging;

import com.wattpilot.charging.entity.ChargingFailureCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Map;

/**
 * Demo/test-only failure injection for {@code MockChargingAdapter}. Empty/off by default, so mock
 * charging always succeeds unless a deployment explicitly opts in — the same "off unless asked" stance
 * as the scheduler's own {@code enabled} flag.
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
 * <p>{@code randomFailureEnabled} and {@code randomFailureRate} are a separate, independent knob: when
 * enabled, every schedule with no explicit {@code failures} entry gets a random chance of a business
 * failure instead of an automatic success, so demo/history data does not look artificially perfect. An
 * explicit {@code failures} entry for a schedule id always takes precedence over the random roll.
 * {@code randomFailureRate} is the probability that a whole session ends in {@code FAILED} - not the
 * probability rolled at each of the start/completion phases individually; {@code MockChargingAdapter}
 * derives the (lower) per-phase roll from it so the two chances a session gets to fail add up to this
 * rate rather than compounding past it.
 *
 * @param failures charging-schedule id → the failure the adapter should simulate for it
 * @param randomFailureEnabled whether unconfigured schedules are subject to a random failure chance
 * @param randomFailureRate probability (0.0-1.0) that an unconfigured schedule's session ends in
 *                           FAILED overall, when enabled
 */
@ConfigurationProperties("wattpilot.charging.execution.mock")
public record MockChargingProperties(
        Map<Long, ChargingFailureCode> failures, boolean randomFailureEnabled, double randomFailureRate) {

    public MockChargingProperties(
            Map<Long, ChargingFailureCode> failures,
            @DefaultValue("false") boolean randomFailureEnabled,
            @DefaultValue("0.10") double randomFailureRate) {
        Map<Long, ChargingFailureCode> copy = failures == null ? Map.of() : Map.copyOf(failures);
        copy.forEach((scheduleId, code) -> {
            if (code == ChargingFailureCode.MISSED_EXECUTION_WINDOW) {
                throw new IllegalArgumentException(
                        "MISSED_EXECUTION_WINDOW cannot be injected via wattpilot.charging.execution.mock.failures "
                                + "(schedule id=" + scheduleId + "); it is not a Mock Charging adapter outcome");
            }
        });
        if (randomFailureRate < 0.0 || randomFailureRate > 1.0) {
            throw new IllegalArgumentException(
                    "wattpilot.charging.execution.mock.random-failure-rate must be between 0.0 and 1.0, got "
                            + randomFailureRate);
        }
        this.failures = copy;
        this.randomFailureEnabled = randomFailureEnabled;
        this.randomFailureRate = randomFailureRate;
    }
}
