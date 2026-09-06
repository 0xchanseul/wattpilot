package com.wattpilot.charging.port;

import com.wattpilot.charging.entity.ChargingFailureCode;

/**
 * The definitive result of one {@link ChargingExecutionPort} call. A method that cannot even reach the
 * underlying system throws instead of returning a {@link Failure} — see {@link ChargingExecutionPort}.
 */
public sealed interface ExecutionOutcome {

    static ExecutionOutcome success() {
        return new Success();
    }

    static ExecutionOutcome failure(ChargingFailureCode failureCode, String failureReason) {
        return new Failure(failureCode, failureReason);
    }

    record Success() implements ExecutionOutcome {
    }

    record Failure(ChargingFailureCode failureCode, String failureReason) implements ExecutionOutcome {
    }
}
