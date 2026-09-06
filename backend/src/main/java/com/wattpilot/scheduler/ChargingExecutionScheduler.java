package com.wattpilot.scheduler;

import com.wattpilot.charging.ChargingExecutionProperties;
import com.wattpilot.charging.entity.ChargingScheduleStatus;
import com.wattpilot.charging.repository.ChargingScheduleRepository;
import com.wattpilot.charging.service.ChargingExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Consumer;

/**
 * Fires the 1-minute charging-execution tick: completes schedules whose window has closed, starts
 * schedules whose window has opened, and finalizes schedules that missed their window entirely.
 * Completion runs before start/missed so a schedule that just freed up cannot be double-counted within
 * the same tick (the three read-state queries are mutually exclusive by construction regardless, but
 * this ordering also means a slot a completion just vacated is not needed for the next scan to be correct).
 *
 * <p>Calls {@link ChargingExecutionService} directly — no HTTP call to this application's own API — and
 * relies on it to give each schedule id its own transaction, so one schedule's failure never blocks the
 * rest of the batch; a failure that still escapes (e.g. after rollback) is caught and logged here so the
 * remaining ids in the batch are unaffected.
 */
@Component
@ConditionalOnProperty(prefix = "wattpilot.charging.execution", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class ChargingExecutionScheduler {

    private static final Logger log = LoggerFactory.getLogger(ChargingExecutionScheduler.class);

    private final ChargingScheduleRepository scheduleRepository;
    private final ChargingExecutionService executionService;
    private final ChargingExecutionProperties properties;
    private final Clock clock;

    public ChargingExecutionScheduler(ChargingScheduleRepository scheduleRepository,
                                      ChargingExecutionService executionService,
                                      ChargingExecutionProperties properties,
                                      Clock clock) {
        this.scheduleRepository = scheduleRepository;
        this.executionService = executionService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${wattpilot.charging.execution.cron:0 * * * * *}")
    public void run() {
        runOnce();
    }

    /** Runs one tick synchronously; exposed so tests can drive it deterministically with a fixed Clock. */
    public void runOnce() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Limit limit = Limit.of(properties.batchSize());

        log.debug("Charging execution tick at {}", now);

        int completed = process("complete",
                scheduleRepository.findReadyToCompleteIds(ChargingScheduleStatus.IN_PROGRESS, now, limit),
                executionService::attemptComplete);
        int started = process("start",
                scheduleRepository.findReadyToStartIds(ChargingScheduleStatus.WAITING, now, limit),
                executionService::attemptStart);
        int missed = process("missed",
                scheduleRepository.findMissedIds(ChargingScheduleStatus.WAITING, now, limit),
                executionService::markMissed);

        // Only speak up on a tick that had work to do; an idle minute stays at DEBUG above.
        if (completed + started + missed > 0) {
            log.info("Charging execution tick processed {} schedule(s): start={}, complete={}, missed={}",
                    completed + started + missed, started, completed, missed);
        }
    }

    /** @return the number of schedule ids this phase attempted (regardless of their individual outcome) */
    private int process(String phase, List<Long> scheduleIds, Consumer<Long> action) {
        for (Long scheduleId : scheduleIds) {
            try {
                action.accept(scheduleId);
            } catch (Exception e) {
                log.error("Unexpected error in {} phase for charging schedule id={}", phase, scheduleId, e);
            }
        }
        return scheduleIds.size();
    }
}
