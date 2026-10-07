package com.wattpilot.auth.service;

import com.wattpilot.auth.DemoProperties;
import com.wattpilot.charging.service.ChargingScheduleService;
import com.wattpilot.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoAccountCleanupServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

    @Mock
    private UserService userService;

    @Mock
    private ChargingScheduleService chargingScheduleService;

    private DemoAccountCleanupService cleanupService;

    @BeforeEach
    void setUp() {
        DemoProperties properties = new DemoProperties(true, "demo@example.com", Duration.ofHours(24), 200, 50);
        cleanupService = new DemoAccountCleanupService(
                properties, userService, chargingScheduleService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void looksForAccountsOlderThanTheTtlWithinTheBatchSize() {
        when(userService.findExpiredDemoUserIds(any(), anyInt())).thenReturn(List.of());

        int deleted = cleanupService.deleteExpiredAccounts();

        assertThat(deleted).isZero();
        verify(userService).findExpiredDemoUserIds(OffsetDateTime.parse("2026-10-06T12:00:00Z"), 50);
    }

    @Test
    void deletesEveryExpiredAccountAndReportsTheCount() {
        when(userService.findExpiredDemoUserIds(any(), anyInt())).thenReturn(List.of(1L, 2L));
        when(userService.deleteDemoAccount(1L)).thenReturn(true);
        when(userService.deleteDemoAccount(2L)).thenReturn(true);

        assertThat(cleanupService.deleteExpiredAccounts()).isEqualTo(2);
    }

    @Test
    void leavesAnAccountWithARunningChargeForALaterRun() {
        when(userService.findExpiredDemoUserIds(any(), anyInt())).thenReturn(List.of(1L, 2L));
        when(chargingScheduleService.hasInProgressSchedule(1L)).thenReturn(true);
        when(userService.deleteDemoAccount(2L)).thenReturn(true);

        int deleted = cleanupService.deleteExpiredAccounts();

        assertThat(deleted).isEqualTo(1);
        verify(userService, never()).deleteDemoAccount(1L);
    }

    @Test
    void aFailureOnOneAccountDoesNotStopTheRest() {
        when(userService.findExpiredDemoUserIds(any(), anyInt())).thenReturn(List.of(1L, 2L));
        when(userService.deleteDemoAccount(1L)).thenThrow(new IllegalStateException("database unavailable"));
        when(userService.deleteDemoAccount(2L)).thenReturn(true);

        int deleted = cleanupService.deleteExpiredAccounts();

        assertThat(deleted).isEqualTo(1);
        verify(userService).deleteDemoAccount(2L);
    }

    @Test
    void doesNotCountAnAccountThatWasAlreadyGone() {
        when(userService.findExpiredDemoUserIds(any(), anyInt())).thenReturn(List.of(1L));
        when(userService.deleteDemoAccount(1L)).thenReturn(false);

        assertThat(cleanupService.deleteExpiredAccounts()).isZero();
    }
}
