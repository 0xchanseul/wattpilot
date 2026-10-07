package com.wattpilot.auth.service;

import com.wattpilot.auth.DemoProperties;
import com.wattpilot.charging.service.DemoChargingHistoryService;
import com.wattpilot.common.PriceArea;
import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import com.wattpilot.ev.service.DemoEvService;
import com.wattpilot.user.entity.User;
import com.wattpilot.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoAccountServiceTest {

    private static final String TEMPLATE_EMAIL = "demo@example.com";
    private static final long TEMPLATE_ID = 1L;
    private static final long VISITOR_ID = 2L;

    @Mock
    private UserService userService;

    @Mock
    private DemoEvService demoEvService;

    @Mock
    private DemoChargingHistoryService demoChargingHistoryService;

    @Test
    void aDisabledDemoIsUnavailableAndTouchesNothing() {
        DemoAccountService service = serviceWith(false, 200);

        assertThatThrownBy(service::createAccount)
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.DEMO_UNAVAILABLE);
        verifyNoInteractions(userService, demoEvService, demoChargingHistoryService);
    }

    @Test
    void aMissingTemplateAccountIsUnavailable() {
        when(userService.findByEmail(TEMPLATE_EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(serviceWith(true, 200)::createAccount)
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.DEMO_UNAVAILABLE);
        verify(userService, never()).registerDemo(any());
    }

    @Test
    void reachingTheAccountLimitIsRefusedBeforeAnythingIsCreated() {
        when(userService.findByEmail(TEMPLATE_EMAIL)).thenReturn(Optional.of(user(TEMPLATE_ID)));
        when(userService.countDemoAccounts()).thenReturn(5L);

        assertThatThrownBy(serviceWith(true, 5)::createAccount)
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.DEMO_CAPACITY_REACHED);
        verify(userService, never()).registerDemo(any());
    }

    @Test
    void aTemplateWithoutAnActiveEvIsUnavailable() {
        when(userService.findByEmail(TEMPLATE_EMAIL)).thenReturn(Optional.of(user(TEMPLATE_ID)));
        when(userService.countDemoAccounts()).thenReturn(0L);
        when(userService.registerDemo(PriceArea.NO3)).thenReturn(user(VISITOR_ID));
        when(demoEvService.copyDemoEvs(TEMPLATE_ID, VISITOR_ID)).thenReturn(Map.of());

        assertThatThrownBy(serviceWith(true, 200)::createAccount)
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.DEMO_UNAVAILABLE);
        verifyNoInteractions(demoChargingHistoryService);
    }

    @Test
    void aVisitorAccountInheritsThePriceAreaAndReceivesCopiesOfTheTemplateEvsAndTheirHistory() {
        when(userService.findByEmail(TEMPLATE_EMAIL)).thenReturn(Optional.of(user(TEMPLATE_ID)));
        when(userService.countDemoAccounts()).thenReturn(4L);
        User visitor = user(VISITOR_ID);
        when(userService.registerDemo(PriceArea.NO3)).thenReturn(visitor);
        Map<Long, Long> copiedEvs = Map.of(10L, 20L, 11L, 21L);
        when(demoEvService.copyDemoEvs(TEMPLATE_ID, VISITOR_ID)).thenReturn(copiedEvs);

        User created = serviceWith(true, 5).createAccount();

        assertThat(created).isSameAs(visitor);
        verify(demoEvService).copyDemoEvs(TEMPLATE_ID, VISITOR_ID);
        verify(demoChargingHistoryService).copyFinishedHistory(TEMPLATE_ID, VISITOR_ID, copiedEvs);
    }

    @Test
    void theAccountLifetimeIsTheConfiguredTtl() {
        assertThat(serviceWith(false, 200).accountLifetime()).isEqualTo(Duration.ofHours(24));
    }

    private DemoAccountService serviceWith(boolean enabled, int maxActiveAccounts) {
        return new DemoAccountService(
                new DemoProperties(enabled, TEMPLATE_EMAIL, Duration.ofHours(24), maxActiveAccounts, 200),
                userService, demoEvService, demoChargingHistoryService);
    }

    private static User user(long id) {
        User user = User.register("user" + id + "@example.com", "hash", "User", PriceArea.NO3);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
