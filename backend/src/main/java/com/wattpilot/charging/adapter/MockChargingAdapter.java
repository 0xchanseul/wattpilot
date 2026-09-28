package com.wattpilot.charging.adapter;

import com.wattpilot.charging.MockChargingProperties;
import com.wattpilot.charging.entity.ChargingFailureCode;
import com.wattpilot.charging.port.ChargingExecutionPort;
import com.wattpilot.charging.port.ExecutionOutcome;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * Simulates vehicle/charger execution instead of calling a real manufacturer API; V1 does not control
 * real EVs. Every call succeeds by default — there is no randomised failure — so ordinary use and the
 * happy-path tests can rely on it.
 *
 * <p>For demos and integration tests that need to see a failure or the retry path, a schedule id can
 * be listed in {@link MockChargingProperties#failures()}: the adapter then produces that exact outcome
 * for that id, deterministically, on the phase the failure code belongs to. A test that needs finer
 * control still mocks {@link ChargingExecutionPort} directly.
 *
 * <p>When {@link MockChargingProperties#randomFailureEnabled()} is on, a schedule with no explicit
 * {@code failures} entry additionally gets a {@link MockChargingProperties#randomFailureRate()} chance
 * of ending in a business failure overall (a random pick among the codes valid for whichever phase it
 * happens on) instead of an automatic success, so demo/history data does not look artificially perfect.
 * {@code randomFailureRate} is a <b>per-session</b> probability, not a per-phase one: since a schedule
 * that fails at start never reaches the completion phase, rolling the configured rate independently at
 * both phases would make a session roughly twice as likely to fail as configured. Each phase instead
 * rolls the lower probability {@code p} that solves {@code 1 - (1 - p)^2 = rate} (see
 * {@link #perPhaseFailureProbability()}), so "at least one of the (up to) two rolls fails" lands on the
 * configured session-level rate. {@code SYSTEM_ERROR} is never picked at random: it is simulated by
 * throwing rather than returning a failure outcome, which would turn "occasionally fails" into
 * "occasionally takes several retries to fail" - a different and confusing demo behaviour.
 */
@Component
public class MockChargingAdapter implements ChargingExecutionPort {

    private enum Phase {START, COMPLETE}

    private static final Map<ChargingFailureCode, String> INJECTED_FAILURE_REASONS = Map.of(
            ChargingFailureCode.CHARGER_UNAVAILABLE, "The charging station could not be reached.",
            ChargingFailureCode.VEHICLE_DISCONNECTED, "The vehicle was not connected when charging was due to start.",
            ChargingFailureCode.START_REJECTED, "The charging station rejected the start command.",
            ChargingFailureCode.CHARGING_INTERRUPTED, "The charging session was interrupted before it completed.");

    private static final List<ChargingFailureCode> START_PHASE_RANDOM_CODES = List.of(
            ChargingFailureCode.CHARGER_UNAVAILABLE,
            ChargingFailureCode.VEHICLE_DISCONNECTED,
            ChargingFailureCode.START_REJECTED);

    private static final List<ChargingFailureCode> COMPLETE_PHASE_RANDOM_CODES = List.of(
            ChargingFailureCode.CHARGING_INTERRUPTED);

    private final MockChargingProperties properties;
    private final Supplier<RandomGenerator> randomSupplier;

    @Autowired
    public MockChargingAdapter(MockChargingProperties properties) {
        this(properties, ThreadLocalRandom::current);
    }

    /** Test-only seam: a fixed {@link RandomGenerator} makes the random-failure roll deterministic. */
    MockChargingAdapter(MockChargingProperties properties, Supplier<RandomGenerator> randomSupplier) {
        this.properties = properties;
        this.randomSupplier = randomSupplier;
    }

    @Override
    public ExecutionOutcome start(Long scheduleId) {
        return outcomeFor(scheduleId, Phase.START);
    }

    @Override
    public ExecutionOutcome complete(Long scheduleId) {
        return outcomeFor(scheduleId, Phase.COMPLETE);
    }

    private ExecutionOutcome outcomeFor(Long scheduleId, Phase phase) {
        ChargingFailureCode injected = properties.failures().get(scheduleId);
        if (injected != null) {
            if (phaseOf(injected) != phase) {
                return ExecutionOutcome.success();
            }
            if (injected == ChargingFailureCode.SYSTEM_ERROR) {
                throw new MockChargingException(
                        "Simulated transient charging error injected for schedule id=" + scheduleId);
            }
            return ExecutionOutcome.failure(injected, INJECTED_FAILURE_REASONS.get(injected));
        }

        if (properties.randomFailureEnabled()
                && randomSupplier.get().nextDouble() < perPhaseFailureProbability()) {
            ChargingFailureCode code = randomCodeFor(phase);
            return ExecutionOutcome.failure(code, INJECTED_FAILURE_REASONS.get(code));
        }
        return ExecutionOutcome.success();
    }

    /**
     * The probability to roll at a single phase so that failing at least once across the (up to) two
     * phases a session goes through lands on {@link MockChargingProperties#randomFailureRate()}
     * overall: solving {@code 1 - (1 - p)^2 = rate} for {@code p} gives {@code p = 1 - sqrt(1 - rate)}.
     */
    private double perPhaseFailureProbability() {
        return 1.0 - Math.sqrt(1.0 - properties.randomFailureRate());
    }

    /**
     * The phase a failure code is simulated on: {@code CHARGING_INTERRUPTED} means a charge that had
     * already started, everything else (including a {@code SYSTEM_ERROR} throw) is simulated at start.
     */
    private static Phase phaseOf(ChargingFailureCode code) {
        return code == ChargingFailureCode.CHARGING_INTERRUPTED ? Phase.COMPLETE : Phase.START;
    }

    private ChargingFailureCode randomCodeFor(Phase phase) {
        List<ChargingFailureCode> candidates =
                phase == Phase.START ? START_PHASE_RANDOM_CODES : COMPLETE_PHASE_RANDOM_CODES;
        return candidates.get(randomSupplier.get().nextInt(candidates.size()));
    }
}
