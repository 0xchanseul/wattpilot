package com.wattpilot.charging.adapter;

/**
 * Signals a simulated transient technical failure from {@link MockChargingAdapter}, so
 * {@code ChargingExecutionService} exercises its bounded-retry path exactly as it would for a real
 * adapter that could not reach the underlying system. Injected via
 * {@code wattpilot.charging.execution.mock.failures} with the {@code SYSTEM_ERROR} code.
 */
public class MockChargingException extends RuntimeException {

    public MockChargingException(String message) {
        super(message);
    }
}
