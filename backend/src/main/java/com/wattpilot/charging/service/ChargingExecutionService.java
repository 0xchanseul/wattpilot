package com.wattpilot.charging.service;

import com.wattpilot.charging.ChargingExecutionProperties;
import com.wattpilot.charging.entity.ChargingFailureCode;
import com.wattpilot.charging.entity.ChargingPlan;
import com.wattpilot.charging.entity.ChargingSchedule;
import com.wattpilot.charging.entity.ChargingScheduleStatus;
import com.wattpilot.charging.entity.ChargingSession;
import com.wattpilot.charging.port.ChargingExecutionPort;
import com.wattpilot.charging.port.ExecutionOutcome;
import com.wattpilot.charging.repository.ChargingPlanRepository;
import com.wattpilot.charging.repository.ChargingScheduleRepository;
import com.wattpilot.charging.repository.ChargingSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Executes one charging schedule's start or completion attempt, and finalizes one that missed its
 * window. Called once per schedule id by {@code ChargingExecutionScheduler}; every method here is its
 * own transaction so one schedule's outcome never affects another's.
 *
 * <p>Two kinds of failure are handled differently:
 * <ul>
 *   <li><b>Business/definitive failure</b> — {@link ChargingExecutionPort} returns an
 *       {@link ExecutionOutcome.Failure}, or a transient error's retry budget is exhausted. The
 *       schedule and session are moved to {@code FAILED} with a safe {@code failureReason} and the
 *       transaction commits.</li>
 *   <li><b>Transient technical error</b> — {@link ChargingExecutionPort} throws. Caught here, so the
 *       retry bookkeeping commits normally; the schedule stays in its current status and is retried
 *       after a backoff, up to {@link ChargingExecutionProperties#retryMaxAttempts()}.</li>
 * </ul>
 * A failure at the database/transaction layer itself (not from the port call) is deliberately left
 * uncaught: the transaction rolls back, nothing is persisted (not even a retry count increment), and
 * the next scheduler tick's read-state query naturally retries the whole attempt for free.
 */
@Service
public class ChargingExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ChargingExecutionService.class);

    private static final String SYSTEM_ERROR_REASON =
            "A system error prevented this charging execution from completing. Please contact support if this persists.";
    private static final String MISSED_WINDOW_REASON =
            "The charging window closed before this reservation could be started.";

    private final ChargingScheduleRepository scheduleRepository;
    private final ChargingSessionRepository sessionRepository;
    private final ChargingPlanRepository planRepository;
    private final ChargingExecutionPort executionPort;
    private final ChargingExecutionProperties properties;
    private final Clock clock;

    public ChargingExecutionService(ChargingScheduleRepository scheduleRepository,
                                    ChargingSessionRepository sessionRepository,
                                    ChargingPlanRepository planRepository,
                                    ChargingExecutionPort executionPort,
                                    ChargingExecutionProperties properties,
                                    Clock clock) {
        this.scheduleRepository = scheduleRepository;
        this.sessionRepository = sessionRepository;
        this.planRepository = planRepository;
        this.executionPort = executionPort;
        this.properties = properties;
        this.clock = clock;
    }

    /** Attempts to start a {@link ChargingScheduleStatus#WAITING} schedule whose window has opened. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void attemptStart(Long scheduleId) {
        ChargingSchedule schedule = scheduleRepository.findByIdForUpdate(scheduleId).orElse(null);
        if (schedule == null || schedule.getStatus() != ChargingScheduleStatus.WAITING) {
            return;
        }

        ExecutionOutcome outcome;
        try {
            outcome = executionPort.start(scheduleId);
        } catch (RuntimeException transientError) {
            log.warn("Transient error starting charging schedule id={}", scheduleId, transientError);
            applyTransientFailure(schedule, null);
            return;
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        if (outcome instanceof ExecutionOutcome.Success) {
            sessionRepository.save(ChargingSession.started(schedule.getId(), now));
            schedule.markInProgress();
        } else {
            ExecutionOutcome.Failure failure = (ExecutionOutcome.Failure) outcome;
            sessionRepository.save(ChargingSession.failed(schedule.getId(), null,
                    failure.failureCode(), failure.failureReason()));
            schedule.markFailed();
        }
    }

    /** Attempts to complete an {@link ChargingScheduleStatus#IN_PROGRESS} schedule whose window has closed. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void attemptComplete(Long scheduleId) {
        ChargingSchedule schedule = scheduleRepository.findByIdForUpdate(scheduleId).orElse(null);
        if (schedule == null || schedule.getStatus() != ChargingScheduleStatus.IN_PROGRESS) {
            return;
        }
        ChargingSession session = sessionRepository.findByChargingScheduleId(schedule.getId()).orElse(null);
        if (session == null) {
            log.error("IN_PROGRESS charging schedule id={} has no session; leaving for manual review", scheduleId);
            return;
        }

        ExecutionOutcome outcome;
        try {
            outcome = executionPort.complete(scheduleId);
        } catch (RuntimeException transientError) {
            log.warn("Transient error completing charging schedule id={}", scheduleId, transientError);
            applyTransientFailure(schedule, session);
            return;
        }

        if (outcome instanceof ExecutionOutcome.Success) {
            ChargingPlan plan = planRepository.findById(schedule.getChargingPlanId()).orElseThrow(
                    () -> new IllegalStateException("Charging plan not found for schedule id=" + scheduleId));
            ChargingResultCalculator.Result result = ChargingResultCalculator.fromPlanSnapshot(plan);
            session.complete(OffsetDateTime.now(clock), result.actualEnergyKwh(), result.actualCostNok(),
                    result.baselineCostNok(), result.optimizedCostNok(), result.estimatedSavingsNok());
            schedule.markCompleted();
        } else {
            ExecutionOutcome.Failure failure = (ExecutionOutcome.Failure) outcome;
            session.fail(failure.failureCode(), failure.failureReason());
            schedule.markFailed();
        }
    }

    /** Finalizes a {@link ChargingScheduleStatus#WAITING} schedule whose window closed before it ever started. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markMissed(Long scheduleId) {
        ChargingSchedule schedule = scheduleRepository.findByIdForUpdate(scheduleId).orElse(null);
        if (schedule == null || schedule.getStatus() != ChargingScheduleStatus.WAITING) {
            return;
        }
        sessionRepository.save(ChargingSession.failed(schedule.getId(), null,
                ChargingFailureCode.MISSED_EXECUTION_WINDOW, MISSED_WINDOW_REASON));
        schedule.markFailed();
    }

    /**
     * Records one more failed attempt. Below the retry budget, backs off and leaves the schedule (and,
     * for the completion phase, its existing session) untouched otherwise. At the budget, finalizes
     * both as FAILED(SYSTEM_ERROR) without ever exposing the underlying exception to the user.
     *
     * @param existingSession the schedule's session if this is a completion-phase retry, else {@code null}
     */
    private void applyTransientFailure(ChargingSchedule schedule, ChargingSession existingSession) {
        int attempts = schedule.getRetryCount() + 1;
        if (attempts >= properties.retryMaxAttempts()) {
            if (existingSession != null) {
                existingSession.fail(ChargingFailureCode.SYSTEM_ERROR, SYSTEM_ERROR_REASON);
            } else {
                sessionRepository.save(ChargingSession.failed(schedule.getId(), null,
                        ChargingFailureCode.SYSTEM_ERROR, SYSTEM_ERROR_REASON));
            }
            schedule.markFailed();
            return;
        }

        long backoffSeconds = Math.round(properties.retryInitialBackoff().toSeconds()
                * Math.pow(properties.retryBackoffMultiplier(), attempts - 1));
        schedule.scheduleRetry(attempts, OffsetDateTime.now(clock).plusSeconds(backoffSeconds));
    }
}
