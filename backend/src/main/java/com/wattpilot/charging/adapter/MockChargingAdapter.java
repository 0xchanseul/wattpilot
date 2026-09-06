package com.wattpilot.charging.adapter;

import com.wattpilot.charging.port.ChargingExecutionPort;
import com.wattpilot.charging.port.ExecutionOutcome;
import org.springframework.stereotype.Component;

/**
 * Simulates vehicle/charger execution instead of calling a real manufacturer API; V1 does not control
 * real EVs. Every call succeeds deterministically — no randomised failure — so a test that needs a
 * business failure or a transient error stubs or mocks {@link ChargingExecutionPort} explicitly rather
 * than relying on behavior here.
 */
@Component
public class MockChargingAdapter implements ChargingExecutionPort {

    @Override
    public ExecutionOutcome start(Long scheduleId) {
        return ExecutionOutcome.success();
    }

    @Override
    public ExecutionOutcome complete(Long scheduleId) {
        return ExecutionOutcome.success();
    }
}
