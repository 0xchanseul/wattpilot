package com.wattpilot.charging.adapter;

import com.wattpilot.charging.MockChargingProperties;
import com.wattpilot.charging.entity.ChargingFailureCode;
import com.wattpilot.charging.port.ExecutionOutcome;
import org.junit.jupiter.api.Test;

import java.util.Map;

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
        return new MockChargingAdapter(new MockChargingProperties(failures));
    }
}
