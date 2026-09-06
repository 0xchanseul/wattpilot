package com.wattpilot.scheduler;

import com.wattpilot.charging.ChargingExecutionProperties;
import com.wattpilot.charging.entity.ChargingScheduleStatus;
import com.wattpilot.charging.repository.ChargingScheduleRepository;
import com.wattpilot.charging.service.ChargingExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChargingExecutionSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-09-05T13:00:00Z");

    @Mock private ChargingScheduleRepository scheduleRepository;
    @Mock private ChargingExecutionService executionService;

    private ChargingExecutionScheduler scheduler;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        ChargingExecutionProperties properties = new ChargingExecutionProperties(
                true, "0 * * * * *", 100, 3, Duration.ofMinutes(1), 2.0);
        scheduler = new ChargingExecutionScheduler(scheduleRepository, executionService, properties, clock);
    }

    @Test
    void processesCompletionBeforeStartBeforeMissed() {
        when(scheduleRepository.findReadyToCompleteIds(eq(ChargingScheduleStatus.IN_PROGRESS), any(), any(Limit.class)))
                .thenReturn(List.of(1L));
        when(scheduleRepository.findReadyToStartIds(eq(ChargingScheduleStatus.WAITING), any(), any(Limit.class)))
                .thenReturn(List.of(2L));
        when(scheduleRepository.findMissedIds(eq(ChargingScheduleStatus.WAITING), any(), any(Limit.class)))
                .thenReturn(List.of(3L));

        scheduler.runOnce();

        InOrder order = inOrder(executionService);
        order.verify(executionService).attemptComplete(1L);
        order.verify(executionService).attemptStart(2L);
        order.verify(executionService).markMissed(3L);
    }

    @Test
    void oneFailingScheduleDoesNotStopTheRestOfTheBatch() {
        when(scheduleRepository.findReadyToCompleteIds(any(), any(), any(Limit.class))).thenReturn(List.of());
        when(scheduleRepository.findReadyToStartIds(any(), any(), any(Limit.class))).thenReturn(List.of(1L, 2L, 3L));
        when(scheduleRepository.findMissedIds(any(), any(), any(Limit.class))).thenReturn(List.of());
        doThrow(new RuntimeException("boom")).when(executionService).attemptStart(2L);

        scheduler.runOnce();

        verify(executionService).attemptStart(1L);
        verify(executionService).attemptStart(2L);
        verify(executionService).attemptStart(3L);
    }
}
