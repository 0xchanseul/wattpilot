package com.wattpilot.auth.service;

import com.wattpilot.auth.DemoProperties;
import com.wattpilot.charging.service.ChargingScheduleService;
import com.wattpilot.common.PriceArea;
import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import com.wattpilot.ev.service.VehicleConnectionService;
import com.wattpilot.user.entity.User;
import com.wattpilot.user.service.UserService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceTest {

    private static final Long USER_ID = 7L;
    private static final String PASSWORD = "wattpilot-secret";
    private static final String HASH = "{bcrypt}hash";

    @Mock
    private UserService userService;

    @Mock
    private ChargingScheduleService chargingScheduleService;

    @Mock
    private VehicleConnectionService vehicleConnectionService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AccountDeletionService service;

    @BeforeEach
    void setUp() {
        DemoProperties demoProperties = new DemoProperties(true, "Template@Example.com", Duration.ofHours(24), 200, 50);
        service = new AccountDeletionService(userService, chargingScheduleService, vehicleConnectionService,
                passwordEncoder, demoProperties);
    }

    @Test
    void removesSmartcarConnectionsBeforeDeletingTheAccount() {
        when(userService.getById(USER_ID)).thenReturn(regularUser("iris@example.com"));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(chargingScheduleService.hasInProgressSchedule(USER_ID)).thenReturn(false);

        service.deleteAccount(USER_ID, PASSWORD);

        InOrder order = inOrder(vehicleConnectionService, userService);
        order.verify(vehicleConnectionService).removeAllForUser(USER_ID);
        order.verify(userService).deleteRegularAccount(USER_ID);
    }

    @Test
    void rejectsAWrongPasswordWithoutTouchingAnyData() {
        when(userService.getById(USER_ID)).thenReturn(regularUser("iris@example.com"));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(false);

        assertThat(errorOf(() -> service.deleteAccount(USER_ID, PASSWORD))).isEqualTo(ErrorCode.INCORRECT_PASSWORD);

        verify(vehicleConnectionService, never()).removeAllForUser(USER_ID);
        verify(userService, never()).deleteRegularAccount(USER_ID);
    }

    @Test
    void refusesWhileAChargeIsInProgress() {
        when(userService.getById(USER_ID)).thenReturn(regularUser("iris@example.com"));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(chargingScheduleService.hasInProgressSchedule(USER_ID)).thenReturn(true);

        assertThat(errorOf(() -> service.deleteAccount(USER_ID, PASSWORD)))
                .isEqualTo(ErrorCode.ACCOUNT_CHARGING_IN_PROGRESS);

        verify(vehicleConnectionService, never()).removeAllForUser(USER_ID);
        verify(userService, never()).deleteRegularAccount(USER_ID);
    }

    @Test
    void refusesADemoVisitorAccount() {
        when(userService.getById(USER_ID)).thenReturn(
                User.registerDemo("demo-1@demo.wattpilot.invalid", HASH, "Demo visitor", PriceArea.NO1));

        assertThat(errorOf(() -> service.deleteAccount(USER_ID, PASSWORD)))
                .isEqualTo(ErrorCode.ACCOUNT_DELETION_NOT_ALLOWED);

        verify(userService, never()).deleteRegularAccount(USER_ID);
    }

    @Test
    void refusesTheSharedDemoTemplateAccountRegardlessOfEmailCase() {
        when(userService.getById(USER_ID)).thenReturn(regularUser("template@example.com"));

        assertThat(errorOf(() -> service.deleteAccount(USER_ID, PASSWORD)))
                .isEqualTo(ErrorCode.ACCOUNT_DELETION_NOT_ALLOWED);

        verify(userService, never()).deleteRegularAccount(USER_ID);
    }

    private static User regularUser(String email) {
        return User.register(email, HASH, "Iris", PriceArea.NO1);
    }

    private static ErrorCode errorOf(ThrowingCallable call) {
        return catchThrowableOfType(BusinessException.class, call).errorCode();
    }
}
