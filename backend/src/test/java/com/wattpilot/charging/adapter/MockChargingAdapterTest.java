package com.wattpilot.charging.adapter;

import com.wattpilot.charging.MockChargingProperties;
import com.wattpilot.charging.entity.ChargingFailureCode;
import com.wattpilot.charging.port.ExecutionOutcome;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Random;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockChargingAdapterTest {

    private static final long SCHEDULE_ID = 42L;
    private static final long OTHER_SCHEDULE_ID = 99L;

    @Test
    void succeedsForBothPhasesWhenNoFailuresAreConfigured() {
        MockChargingAdapter adapter = adapterWith(Map.of());

        assertThat(adapter.start(SCHEDULE_ID)).isInstanceOf(ExecutionOutcome.Success.class);
        assertThat(adapter.complete(SCHEDULE_ID)).isInstanceOf(ExecutionOutcome.Success.class);
    }

    @Test
    void succeedsWhenRandomFailureIsEnabledButTheRollLandsAboveThePerPhaseProbability() {
        MockChargingProperties properties = new MockChargingProperties(Map.of(), true, 0.10);
        MockChargingAdapter adapter = new MockChargingAdapter(properties, () -> fixedDouble(0.5));

        assertThat(adapter.start(SCHEDULE_ID)).isInstanceOf(ExecutionOutcome.Success.class);
    }

    @Test
    void derivesALowerPerPhaseProbabilityFromTheConfiguredPerSessionRate() {
        // rate=0.10 -> per-phase p = 1 - sqrt(0.9) ~= 0.0513. A roll of 0.06 is below the configured
        // rate but above the derived per-phase probability, so it must succeed: rolling the raw 10%
        // independently at both start and complete would make a session ~19% likely to fail overall,
        // not the agreed 10%.
        MockChargingProperties properties = new MockChargingProperties(Map.of(), true, 0.10);
        MockChargingAdapter adapter = new MockChargingAdapter(properties, () -> fixedDouble(0.06));

        assertThat(adapter.start(SCHEDULE_ID)).isInstanceOf(ExecutionOutcome.Success.class);
        assertThat(adapter.complete(SCHEDULE_ID)).isInstanceOf(ExecutionOutcome.Success.class);
    }

    @Test
    void injectsARandomStartPhaseFailureWhenTheRollLandsBelowThePerPhaseProbability() {
        MockChargingProperties properties = new MockChargingProperties(Map.of(), true, 0.10);
        MockChargingAdapter adapter = new MockChargingAdapter(properties, () -> fixedDouble(0.0));

        ExecutionOutcome outcome = adapter.start(SCHEDULE_ID);

        assertThat(outcome).isInstanceOfSatisfying(ExecutionOutcome.Failure.class, failure ->
                assertThat(failure.failureCode()).isIn(ChargingFailureCode.CHARGER_UNAVAILABLE,
                        ChargingFailureCode.VEHICLE_DISCONNECTED, ChargingFailureCode.START_REJECTED));
    }

    @Test
    void injectsOnlyChargingInterruptedAsARandomCompletionPhaseFailure() {
        MockChargingProperties properties = new MockChargingProperties(Map.of(), true, 0.10);
        MockChargingAdapter adapter = new MockChargingAdapter(properties, () -> fixedDouble(0.0));

        ExecutionOutcome outcome = adapter.complete(SCHEDULE_ID);

        assertThat(outcome).isInstanceOfSatisfying(ExecutionOutcome.Failure.class, failure ->
                assertThat(failure.failureCode()).isEqualTo(ChargingFailureCode.CHARGING_INTERRUPTED));
    }

    @Test
    void neverRandomlyFailsWhenRandomFailureIsDisabled() {
        MockChargingProperties properties = new MockChargingProperties(Map.of(), false, 1.0);
        MockChargingAdapter adapter = new MockChargingAdapter(properties, () -> fixedDouble(0.0));

        assertThat(adapter.start(SCHEDULE_ID)).isInstanceOf(ExecutionOutcome.Success.class);
    }

    @Test
    void anExplicitFailuresEntryTakesPrecedenceOverTheRandomRoll() {
        MockChargingProperties properties =
                new MockChargingProperties(Map.of(SCHEDULE_ID, ChargingFailureCode.START_REJECTED), true, 1.0);
        MockChargingAdapter adapter = new MockChargingAdapter(properties, () -> fixedDouble(0.0));

        ExecutionOutcome outcome = adapter.start(SCHEDULE_ID);

        assertThat(outcome).isInstanceOfSatisfying(ExecutionOutcome.Failure.class, failure ->
                assertThat(failure.failureCode()).isEqualTo(ChargingFailureCode.START_REJECTED));
    }

    /** A {@link RandomGenerator} whose {@code nextDouble()} always returns {@code value}. */
    private static RandomGenerator fixedDouble(double value) {
        return new Random() {
            @Override
            public double nextDouble() {
                return value;
            }
        };
    }

    @Test
    void injectsAStartPhaseBusinessFailureOnlyForTheConfiguredSchedule() {
        MockChargingAdapter adapter = adapterWith(Map.of(SCHEDULE_ID, ChargingFailureCode.CHARGER_UNAVAILABLE));

        ExecutionOutcome outcome = adapter.start(SCHEDULE_ID);

        assertThat(outcome).isInstanceOfSatisfying(ExecutionOutcome.Failure.class, failure -> {
            assertThat(failure.failureCode()).isEqualTo(ChargingFailureCode.CHARGER_UNAVAILABLE);
            assertThat(failure.failureReason()).isNotBlank();
        });
        // A start-phase code never affects the completion phase, and never affects a different id.
        assertThat(adapter.complete(SCHEDULE_ID)).isInstanceOf(ExecutionOutcome.Success.class);
        assertThat(adapter.start(OTHER_SCHEDULE_ID)).isInstanceOf(ExecutionOutcome.Success.class);
    }

    @Test
    void injectsACompletionPhaseBusinessFailureOnlyOnTheCompletionPhase() {
        MockChargingAdapter adapter = adapterWith(Map.of(SCHEDULE_ID, ChargingFailureCode.CHARGING_INTERRUPTED));

        assertThat(adapter.start(SCHEDULE_ID)).isInstanceOf(ExecutionOutcome.Success.class);
        assertThat(adapter.complete(SCHEDULE_ID)).isInstanceOfSatisfying(ExecutionOutcome.Failure.class,
                failure -> assertThat(failure.failureCode()).isEqualTo(ChargingFailureCode.CHARGING_INTERRUPTED));
    }

    @Test
    void injectsATransientErrorForASystemErrorMapping() {
        MockChargingAdapter adapter = adapterWith(Map.of(SCHEDULE_ID, ChargingFailureCode.SYSTEM_ERROR));

        assertThatThrownBy(() -> adapter.start(SCHEDULE_ID)).isInstanceOf(MockChargingException.class);
    }

    private static MockChargingAdapter adapterWith(Map<Long, ChargingFailureCode> failures) {
        return new MockChargingAdapter(new MockChargingProperties(failures, false, 0.10));
    }
}
