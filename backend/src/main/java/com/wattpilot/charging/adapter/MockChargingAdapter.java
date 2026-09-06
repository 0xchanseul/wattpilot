package com.wattpilot.charging.adapter;

import com.wattpilot.charging.MockChargingProperties;
import com.wattpilot.charging.entity.ChargingFailureCode;
import com.wattpilot.charging.port.ChargingExecutionPort;
import com.wattpilot.charging.port.ExecutionOutcome;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Simulates vehicle/charger execution instead of calling a real manufacturer API; V1 does not control
 * real EVs. Every call succeeds by default — there is no randomised failure — so ordinary use and the
 * happy-path tests can rely on it.
 *
 * <p>For demos and integration tests that need to see a failure or the retry path, a schedule id can
 * be listed in {@link MockChargingProperties#failures()}: the adapter then produces that exact outcome
 * for that id, deterministically, on the phase the failure code belongs to. A test that needs finer
 * control still mocks {@link ChargingExecutionPort} directly.
 */
@Component
public class MockChargingAdapter implements ChargingExecutionPort {

    private enum Phase {START, COMPLETE}

    private static final Map<ChargingFailureCode, String> INJECTED_FAILURE_REASONS = Map.of(
            ChargingFailureCode.CHARGER_UNAVAILABLE, "The charging station could not be reached.",
            ChargingFailureCode.VEHICLE_DISCONNECTED, "The vehicle was not connected when charging was due to start.",
            ChargingFailureCode.START_REJECTED, "The charging station rejected the start command.",
            ChargingFailureCode.CHARGING_INTERRUPTED, "The charging session was interrupted before it completed.");

    private final MockChargingProperties properties;

    public MockChargingAdapter(MockChargingProperties properties) {
        this.properties = properties;
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
        if (injected == null || phaseOf(injected) != phase) {
            return ExecutionOutcome.success();
        }
        if (injected == ChargingFailureCode.SYSTEM_ERROR) {
            throw new MockChargingException(
                    "Simulated transient charging error injected for schedule id=" + scheduleId);
        }
        return ExecutionOutcome.failure(injected, INJECTED_FAILURE_REASONS.get(injected));
    }

    /**
     * The phase a failure code is simulated on: {@code CHARGING_INTERRUPTED} means a charge that had
     * already started, everything else (including a {@code SYSTEM_ERROR} throw) is simulated at start.
     */
    private static Phase phaseOf(ChargingFailureCode code) {
        return code == ChargingFailureCode.CHARGING_INTERRUPTED ? Phase.COMPLETE : Phase.START;
    }
}
