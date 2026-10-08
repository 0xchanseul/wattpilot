package com.wattpilot.ev.service;

import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import com.wattpilot.ev.dto.LinkVehicleConnectionRequest;
import com.wattpilot.ev.dto.VehicleConnectionResponse;
import com.wattpilot.ev.entity.Ev;
import com.wattpilot.ev.entity.VehicleConnection;
import com.wattpilot.ev.repository.VehicleConnectionRepository;
import com.wattpilot.integration.smartcar.SmartcarClient;
import com.wattpilot.integration.smartcar.SmartcarProperties;
import com.wattpilot.integration.smartcar.SmartcarProviderException;
import com.wattpilot.integration.smartcar.dto.SmartcarVehicleCandidate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleConnectionServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long EV_ID = 10L;

    @Mock
    private VehicleConnectionRepository vehicleConnectionRepository;
    @Mock
    private EvService evService;
    @Mock
    private SmartcarClient smartcarClient;
    @Mock
    private SmartcarConnectStateService stateService;
    @Mock
    private VehicleConnectionRemover remover;

    private final MutableClock clock = new MutableClock(java.time.Instant.parse("2026-10-08T10:00:00Z"));

    /** A clock the test can move forward, to step past the telemetry cache lifetime. */
    private static final class MutableClock extends java.time.Clock {
        private java.time.Instant now;

        MutableClock(java.time.Instant now) {
            this.now = now;
        }

        void advance(java.time.Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
        }

        @Override
        public java.time.Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public java.time.Instant instant() {
            return now;
        }
    }

    private static Ev ev(boolean locked) {
        Ev ev = Ev.register(USER_ID, "My i4", "BMW", "i4 eDrive40",
                new BigDecimal("81.10"), new BigDecimal("11.00"), new BigDecimal("7.40"));
        ReflectionTestUtils.setField(ev, "id", EV_ID);
        ReflectionTestUtils.setField(ev, "locked", locked);
        return ev;
    }

    private VehicleConnectionService service(boolean enabled) {
        SmartcarProperties properties = enabled
                ? new SmartcarProperties(true, "client-id", "secret", "app-id", "https://example.com/callback",
                        "simulated", "https://connect.smartcar.com/oauth/authorize",
                        "https://iam.smartcar.com/oauth2/token", "https://vehicle.api.smartcar.com/v3",
                        Duration.ofSeconds(3), Duration.ofSeconds(5))
                : new SmartcarProperties(false, null, null, null, null, "simulated",
                        "https://connect.smartcar.com/oauth/authorize", "https://iam.smartcar.com/oauth2/token",
                        "https://vehicle.api.smartcar.com/v3", Duration.ofSeconds(3), Duration.ofSeconds(5));
        return new VehicleConnectionService(vehicleConnectionRepository, evService, smartcarClient, stateService,
                properties, remover, clock);
    }

    @Test
    void buildConnectUrlFailsWhenProviderIsNotConfigured() {
        VehicleConnectionService service = service(false);

        assertThatThrownBy(() -> service.buildConnectUrl(USER_ID, EV_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.VEHICLE_PROVIDER_NOT_CONFIGURED);
        verifyNoInteractions(evService);
    }

    @Test
    void buildConnectUrlChecksOwnershipAndAlreadyConnectedBeforeIssuingState() {
        VehicleConnectionService service = service(true);
        when(vehicleConnectionRepository.existsByEvId(EV_ID)).thenReturn(false);
        when(stateService.issue(USER_ID, EV_ID)).thenReturn("state-token");
        when(smartcarClient.buildConnectUrl("state-token", "1")).thenReturn("https://connect.smartcar.com/x");

        String url = service.buildConnectUrl(USER_ID, EV_ID);

        verify(evService).getActiveOwnedEv(USER_ID, EV_ID);
        assertThat(url).isEqualTo("https://connect.smartcar.com/x");
    }

    @Test
    void buildConnectUrlRejectsWhenAlreadyConnected() {
        VehicleConnectionService service = service(true);
        when(vehicleConnectionRepository.existsByEvId(EV_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.buildConnectUrl(USER_ID, EV_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.VEHICLE_ALREADY_CONNECTED);
    }

    @Test
    void linkRejectsAStateIssuedForADifferentEv() {
        VehicleConnectionService service = service(true);
        when(stateService.verify("state", USER_ID)).thenReturn(new SmartcarConnectStateService.Verified(USER_ID, 999L));

        assertThatThrownBy(() -> service.link(USER_ID, EV_ID,
                new LinkVehicleConnectionRequest("state", "sc-user", "veh-1")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.INVALID_VEHICLE_CONNECT_STATE);
    }

    @Test
    void linkRejectsAVehicleNotInTheSmartcarAccount() {
        VehicleConnectionService service = service(true);
        when(stateService.verify("state", USER_ID))
                .thenReturn(new SmartcarConnectStateService.Verified(USER_ID, EV_ID));
        when(vehicleConnectionRepository.existsByEvId(EV_ID)).thenReturn(false);
        when(vehicleConnectionRepository.existsByUserIdAndSmartcarVehicleId(USER_ID, "veh-1")).thenReturn(false);
        when(smartcarClient.listVehicles("sc-user")).thenReturn(List.of());

        assertThatThrownBy(() -> service.link(USER_ID, EV_ID,
                new LinkVehicleConnectionRequest("state", "sc-user", "veh-1")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.INVALID_VEHICLE_CONNECT_STATE);
    }

    @Test
    void linkRejectsAVehicleAlreadyConnectedToAnotherEv() {
        VehicleConnectionService service = service(true);
        when(stateService.verify("state", USER_ID))
                .thenReturn(new SmartcarConnectStateService.Verified(USER_ID, EV_ID));
        when(vehicleConnectionRepository.existsByEvId(EV_ID)).thenReturn(false);
        when(vehicleConnectionRepository.existsByUserIdAndSmartcarVehicleId(USER_ID, "veh-1")).thenReturn(true);

        assertThatThrownBy(() -> service.link(USER_ID, EV_ID,
                new LinkVehicleConnectionRequest("state", "sc-user", "veh-1")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.VEHICLE_ALREADY_CONNECTED);
    }

    @Test
    void linkSavesTheMatchingCandidate() {
        VehicleConnectionService service = service(true);
        when(stateService.verify("state", USER_ID))
                .thenReturn(new SmartcarConnectStateService.Verified(USER_ID, EV_ID));
        when(vehicleConnectionRepository.existsByEvId(EV_ID)).thenReturn(false);
        when(vehicleConnectionRepository.existsByUserIdAndSmartcarVehicleId(USER_ID, "veh-1")).thenReturn(false);
        SmartcarVehicleCandidate candidate = new SmartcarVehicleCandidate("conn-1", "veh-1", "Tesla", "Model 3", 2023);
        when(smartcarClient.listVehicles("sc-user")).thenReturn(List.of(candidate));
        when(vehicleConnectionRepository.save(any(VehicleConnection.class))).thenAnswer(inv -> inv.getArgument(0));

        VehicleConnectionResponse response = service.link(USER_ID, EV_ID,
                new LinkVehicleConnectionRequest("state", "sc-user", "veh-1"));

        ArgumentCaptor<VehicleConnection> captor = ArgumentCaptor.forClass(VehicleConnection.class);
        verify(vehicleConnectionRepository).save(captor.capture());
        assertThat(captor.getValue().getSmartcarVehicleId()).isEqualTo("veh-1");
        assertThat(captor.getValue().getSmartcarConnectionId()).isEqualTo("conn-1");
        assertThat(response.make()).isEqualTo("Tesla");
        assertThat(response.evId()).isEqualTo(EV_ID);
    }

    @Test
    void disconnectDelegatesToTheRemover() {
        VehicleConnectionService service = service(true);
        VehicleConnection connection = VehicleConnection.link(
                USER_ID, EV_ID, "sc-user", "veh-1", "conn-1", "Tesla", "Model 3", 2023);
        when(evService.getActiveOwnedEv(USER_ID, EV_ID)).thenReturn(ev(false));
        when(vehicleConnectionRepository.findByEvIdAndUserId(EV_ID, USER_ID)).thenReturn(Optional.of(connection));

        service.disconnect(USER_ID, EV_ID);

        verify(remover).remove(connection);
    }

    @Test
    void disconnectOnALockedEvIsRejectedAndKeepsTheConnection() {
        VehicleConnectionService service = service(true);
        when(evService.getActiveOwnedEv(USER_ID, EV_ID)).thenReturn(ev(true));

        assertThatThrownBy(() -> service.disconnect(USER_ID, EV_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("The vehicle connection of this demo EV cannot be removed.")
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.EV_LOCKED);

        verifyNoInteractions(remover);
    }

    @Test
    void disconnectOnAnUnlinkedEvIsNotFound() {
        VehicleConnectionService service = service(true);
        when(evService.getActiveOwnedEv(USER_ID, EV_ID)).thenReturn(ev(false));
        when(vehicleConnectionRepository.findByEvIdAndUserId(EV_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.disconnect(USER_ID, EV_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.VEHICLE_CONNECTION_NOT_FOUND);
    }

    @Test
    void repeatedTelemetryReadsWithinTheCacheWindowCallSmartcarOnce() {
        VehicleConnectionService service = service(true);
        VehicleConnection connection = VehicleConnection.link(
                USER_ID, EV_ID, "sc-user", "veh-1", "conn-1", "Tesla", "Model 3", 2023);
        when(vehicleConnectionRepository.findByEvIdAndUserId(EV_ID, USER_ID)).thenReturn(Optional.of(connection));
        when(smartcarClient.readTelemetry("sc-user", "veh-1")).thenReturn(
                new com.wattpilot.integration.smartcar.dto.SmartcarTelemetry(
                        55.0, 210.0, true, false, java.time.OffsetDateTime.now()));

        service.telemetry(USER_ID, EV_ID);
        clock.advance(java.time.Duration.ofSeconds(20));
        service.telemetry(USER_ID, EV_ID);

        org.mockito.Mockito.verify(smartcarClient, org.mockito.Mockito.times(1)).readTelemetry("sc-user", "veh-1");
    }

    @Test
    void telemetryIsReadAgainOnceTheCachedReadingHasExpired() {
        VehicleConnectionService service = service(true);
        VehicleConnection connection = VehicleConnection.link(
                USER_ID, EV_ID, "sc-user", "veh-1", "conn-1", "Tesla", "Model 3", 2023);
        when(vehicleConnectionRepository.findByEvIdAndUserId(EV_ID, USER_ID)).thenReturn(Optional.of(connection));
        when(smartcarClient.readTelemetry("sc-user", "veh-1")).thenReturn(
                new com.wattpilot.integration.smartcar.dto.SmartcarTelemetry(
                        55.0, 210.0, true, false, java.time.OffsetDateTime.now()));

        service.telemetry(USER_ID, EV_ID);
        clock.advance(java.time.Duration.ofSeconds(31));
        service.telemetry(USER_ID, EV_ID);

        org.mockito.Mockito.verify(smartcarClient, org.mockito.Mockito.times(2)).readTelemetry("sc-user", "veh-1");
    }

    @Test
    void aCachedReadingIsNeverServedToAUserWhoDoesNotOwnTheConnection() {
        VehicleConnectionService service = service(true);
        VehicleConnection connection = VehicleConnection.link(
                USER_ID, EV_ID, "sc-user", "veh-1", "conn-1", "Tesla", "Model 3", 2023);
        when(vehicleConnectionRepository.findByEvIdAndUserId(EV_ID, USER_ID)).thenReturn(Optional.of(connection));
        when(vehicleConnectionRepository.findByEvIdAndUserId(EV_ID, 2L)).thenReturn(Optional.empty());
        when(smartcarClient.readTelemetry("sc-user", "veh-1")).thenReturn(
                new com.wattpilot.integration.smartcar.dto.SmartcarTelemetry(
                        55.0, 210.0, true, false, java.time.OffsetDateTime.now()));
        service.telemetry(USER_ID, EV_ID);

        assertThatThrownBy(() -> service.telemetry(2L, EV_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.VEHICLE_CONNECTION_NOT_FOUND);
    }

    @Test
    void telemetryMapsAProviderFailureToVehicleProviderUnavailable() {
        VehicleConnectionService service = service(true);
        VehicleConnection connection = VehicleConnection.link(
                USER_ID, EV_ID, "sc-user", "veh-1", "conn-1", "Tesla", "Model 3", 2023);
        when(vehicleConnectionRepository.findByEvIdAndUserId(EV_ID, USER_ID)).thenReturn(Optional.of(connection));
        when(smartcarClient.readTelemetry("sc-user", "veh-1")).thenThrow(new SmartcarProviderException("boom"));

        assertThatThrownBy(() -> service.telemetry(USER_ID, EV_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.VEHICLE_PROVIDER_UNAVAILABLE);
    }
}
