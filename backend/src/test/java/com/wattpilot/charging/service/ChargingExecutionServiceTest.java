package com.wattpilot.charging.service;

import com.wattpilot.charging.ChargingExecutionProperties;
import com.wattpilot.charging.dto.ChargingCandidate;
import com.wattpilot.charging.dto.EvSnapshot;
import com.wattpilot.charging.entity.ChargingFailureCode;
import com.wattpilot.charging.entity.ChargingPlan;
import com.wattpilot.charging.entity.ChargingSchedule;
import com.wattpilot.charging.entity.ChargingScheduleStatus;
import com.wattpilot.charging.entity.ChargingSession;
import com.wattpilot.charging.entity.ChargingSessionStatus;
import com.wattpilot.charging.port.ChargingExecutionPort;
import com.wattpilot.charging.port.ExecutionOutcome;
import com.wattpilot.charging.repository.ChargingPlanRepository;
import com.wattpilot.charging.repository.ChargingScheduleRepository;
import com.wattpilot.charging.repository.ChargingSessionRepository;
import com.wattpilot.common.PriceArea;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChargingExecutionServiceTest {

    private static final Long SCHEDULE_ID = 42L;
    private static final Long PLAN_ID = 7L;
    private static final Instant NOW = Instant.parse("2026-09-05T13:00:00Z");

    @Mock private ChargingScheduleRepository scheduleRepository;
    @Mock private ChargingSessionRepository sessionRepository;
    @Mock private ChargingPlanRepository planRepository;
    @Mock private ChargingExecutionPort executionPort;

    private ChargingExecutionService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        ChargingExecutionProperties properties = new ChargingExecutionProperties(
                true, "0 * * * * *", 100, 3, Duration.ofMinutes(1), 2.0);
        service = new ChargingExecutionService(scheduleRepository, sessionRepository, planRepository,
                executionPort, properties, clock);
    }

    @Test
    void startsAWaitingScheduleAndCreatesAStartedSession() {
        ChargingSchedule schedule = waitingSchedule();
        when(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
        when(executionPort.start(SCHEDULE_ID)).thenReturn(ExecutionOutcome.success());

        service.attemptStart(SCHEDULE_ID);

        assertThat(schedule.getStatus()).isEqualTo(ChargingScheduleStatus.IN_PROGRESS);
        assertThat(schedule.getRetryCount()).isZero();
        assertThat(schedule.getNextRetryAt()).isNull();

        ArgumentCaptor<ChargingSession> captor = ArgumentCaptor.forClass(ChargingSession.class);
        verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue().getChargingScheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(captor.getValue().getStatus()).isEqualTo(ChargingSessionStatus.STARTED);
        assertThat(captor.getValue().getStartedAt()).isEqualTo(nowAsOffsetDateTime());
    }

    @Test
    void skipsStartingWhenScheduleIsNoLongerWaiting() {
        ChargingSchedule schedule = waitingSchedule();
        schedule.markCancelled();
        when(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).thenReturn(Optional.of(schedule));

        service.attemptStart(SCHEDULE_ID);

        verify(executionPort, never()).start(any());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void businessFailureAtStartFailsTheScheduleAndCreatesAFailedSession() {
        ChargingSchedule schedule = waitingSchedule();
        when(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
        when(executionPort.start(SCHEDULE_ID))
                .thenReturn(ExecutionOutcome.failure(ChargingFailureCode.CHARGER_UNAVAILABLE, "Charger offline"));

        service.attemptStart(SCHEDULE_ID);

        assertThat(schedule.getStatus()).isEqualTo(ChargingScheduleStatus.FAILED);

        ArgumentCaptor<ChargingSession> captor = ArgumentCaptor.forClass(ChargingSession.class);
        verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ChargingSessionStatus.FAILED);
        assertThat(captor.getValue().getStartedAt()).isNull();
        assertThat(captor.getValue().getFailureCode()).isEqualTo(ChargingFailureCode.CHARGER_UNAVAILABLE);
        assertThat(captor.getValue().getFailureReason()).isEqualTo("Charger offline");
    }

    @Test
    void transientErrorAtStartBacksOffWithoutCreatingASession() {
        ChargingSchedule schedule = waitingSchedule();
        when(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
        when(executionPort.start(SCHEDULE_ID)).thenThrow(new RuntimeException("connection reset"));

        service.attemptStart(SCHEDULE_ID);

        assertThat(schedule.getStatus()).isEqualTo(ChargingScheduleStatus.WAITING);
        assertThat(schedule.getRetryCount()).isEqualTo(1);
        assertThat(schedule.getNextRetryAt()).isEqualTo(nowAsOffsetDateTime().plusMinutes(1));
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void transientErrorAtStartExhaustsRetriesAndFinalizesAsSystemError() {
        ChargingSchedule schedule = waitingSchedule();
        schedule.scheduleRetry(2, nowAsOffsetDateTime().minusSeconds(1));
        when(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
        when(executionPort.start(SCHEDULE_ID)).thenThrow(new RuntimeException("connection reset"));

        service.attemptStart(SCHEDULE_ID);

        assertThat(schedule.getStatus()).isEqualTo(ChargingScheduleStatus.FAILED);
        assertThat(schedule.getNextRetryAt()).isNull();

        ArgumentCaptor<ChargingSession> captor = ArgumentCaptor.forClass(ChargingSession.class);
        verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ChargingSessionStatus.FAILED);
        assertThat(captor.getValue().getStartedAt()).isNull();
        assertThat(captor.getValue().getFailureCode()).isEqualTo(ChargingFailureCode.SYSTEM_ERROR);
        assertThat(captor.getValue().getFailureReason()).doesNotContain("connection reset");
    }

    @Test
    void completesAnInProgressScheduleUsingThePlanSnapshot() {
        ChargingSchedule schedule = waitingSchedule();
        schedule.markInProgress();
        ChargingSession session = ChargingSession.started(SCHEDULE_ID, nowAsOffsetDateTime().minusHours(1));
        ChargingPlan plan = plan();

        when(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
        when(sessionRepository.findByChargingScheduleId(SCHEDULE_ID)).thenReturn(Optional.of(session));
        when(executionPort.complete(SCHEDULE_ID)).thenReturn(ExecutionOutcome.success());
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));

        service.attemptComplete(SCHEDULE_ID);

        assertThat(schedule.getStatus()).isEqualTo(ChargingScheduleStatus.COMPLETED);
        assertThat(session.getStatus()).isEqualTo(ChargingSessionStatus.COMPLETED);
        assertThat(session.getCompletedAt()).isEqualTo(nowAsOffsetDateTime());
        assertThat(session.getActualEnergyKwh()).isEqualByComparingTo(plan.getExpectedEnergyKwh());
        assertThat(session.getActualCostNok()).isEqualByComparingTo(plan.getEstimatedCostNok());
        assertThat(session.getBaselineCostNok()).isEqualByComparingTo(plan.getBaselineCostNok());
        assertThat(session.getOptimizedCostNok()).isEqualByComparingTo(plan.getEstimatedCostNok());
        assertThat(session.getEstimatedSavingsNok()).isEqualByComparingTo(plan.getExpectedSavingsNok());
    }

    @Test
    void businessFailureAtCompleteFailsTheScheduleAndTheExistingSession() {
        ChargingSchedule schedule = waitingSchedule();
        schedule.markInProgress();
        ChargingSession session = ChargingSession.started(SCHEDULE_ID, nowAsOffsetDateTime().minusHours(1));

        when(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
        when(sessionRepository.findByChargingScheduleId(SCHEDULE_ID)).thenReturn(Optional.of(session));
        when(executionPort.complete(SCHEDULE_ID))
                .thenReturn(ExecutionOutcome.failure(ChargingFailureCode.CHARGING_INTERRUPTED, "Cable unplugged"));

        service.attemptComplete(SCHEDULE_ID);

        assertThat(schedule.getStatus()).isEqualTo(ChargingScheduleStatus.FAILED);
        assertThat(session.getStatus()).isEqualTo(ChargingSessionStatus.FAILED);
        assertThat(session.getCompletedAt()).isNull();
        assertThat(session.getFailureCode()).isEqualTo(ChargingFailureCode.CHARGING_INTERRUPTED);
        assertThat(session.getFailureReason()).isEqualTo("Cable unplugged");
        verify(planRepository, never()).findById(any());
    }

    @Test
    void transientErrorAtCompleteExhaustsRetriesAndFinalizesTheExistingSessionAsSystemError() {
        ChargingSchedule schedule = waitingSchedule();
        schedule.markInProgress();
        schedule.scheduleRetry(2, nowAsOffsetDateTime().minusSeconds(1));
        ChargingSession session = ChargingSession.started(SCHEDULE_ID, nowAsOffsetDateTime().minusHours(1));

        when(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
        when(sessionRepository.findByChargingScheduleId(SCHEDULE_ID)).thenReturn(Optional.of(session));
        when(executionPort.complete(SCHEDULE_ID)).thenThrow(new RuntimeException("timeout"));

        service.attemptComplete(SCHEDULE_ID);

        assertThat(schedule.getStatus()).isEqualTo(ChargingScheduleStatus.FAILED);
        assertThat(session.getStatus()).isEqualTo(ChargingSessionStatus.FAILED);
        assertThat(session.getFailureCode()).isEqualTo(ChargingFailureCode.SYSTEM_ERROR);
        assertThat(session.getFailureReason()).doesNotContain("timeout");
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void skipsCompletingWhenScheduleIsNoLongerInProgress() {
        ChargingSchedule schedule = waitingSchedule();
        when(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).thenReturn(Optional.of(schedule));

        service.attemptComplete(SCHEDULE_ID);

        verify(executionPort, never()).complete(any());
        verify(sessionRepository, never()).findByChargingScheduleId(any());
    }

    @Test
    void marksAMissedWaitingScheduleAsFailedWithAMissedWindowSession() {
        ChargingSchedule schedule = waitingSchedule();
        when(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).thenReturn(Optional.of(schedule));

        service.markMissed(SCHEDULE_ID);

        assertThat(schedule.getStatus()).isEqualTo(ChargingScheduleStatus.FAILED);

        ArgumentCaptor<ChargingSession> captor = ArgumentCaptor.forClass(ChargingSession.class);
        verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ChargingSessionStatus.FAILED);
        assertThat(captor.getValue().getStartedAt()).isNull();
        assertThat(captor.getValue().getFailureCode()).isEqualTo(ChargingFailureCode.MISSED_EXECUTION_WINDOW);
    }

    @Test
    void markMissedSkipsWhenScheduleIsNoLongerWaiting() {
        ChargingSchedule schedule = waitingSchedule();
        schedule.markInProgress();
        when(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).thenReturn(Optional.of(schedule));

        service.markMissed(SCHEDULE_ID);

        verify(sessionRepository, never()).save(any());
        assertThat(schedule.getStatus()).isEqualTo(ChargingScheduleStatus.IN_PROGRESS);
    }

    private OffsetDateTime nowAsOffsetDateTime() {
        return NOW.atOffset(ZoneOffset.UTC);
    }

    private static ChargingSchedule waitingSchedule() {
        ChargingSchedule schedule = ChargingSchedule.create(PLAN_ID,
                OffsetDateTime.parse("2026-09-05T13:00:00Z"), OffsetDateTime.parse("2026-09-05T15:00:00Z"),
                new BigDecimal("10.00"), new BigDecimal("20.0000"));
        ReflectionTestUtils.setField(schedule, "id", SCHEDULE_ID);
        return schedule;
    }

    private static ChargingPlan plan() {
        ChargingPlan plan = ChargingPlan.succeeded(
                1L, 2L, PriceArea.NO1, new BigDecimal("30"), new BigDecimal("80"),
                OffsetDateTime.parse("2026-09-05T13:00:00Z"), OffsetDateTime.parse("2026-09-05T20:00:00Z"),
                EvSnapshot.from(com.wattpilot.ev.entity.Ev.register(1L, "EV", "Make", "Model",
                        new BigDecimal("60.00"), new BigDecimal("11.00"), new BigDecimal("7.40"))),
                new BigDecimal("9.00"), new BigDecimal("7.40"), 90,
                new ChargingCandidate(1,
                        OffsetDateTime.parse("2026-09-05T13:00:00Z"), OffsetDateTime.parse("2026-09-05T15:00:00Z"),
                        new BigDecimal("10.00"), new BigDecimal("20.0000"), new BigDecimal("30.0000"),
                        new BigDecimal("10.0000"), List.of()));
        ReflectionTestUtils.setField(plan, "id", PLAN_ID);
        return plan;
    }
}
