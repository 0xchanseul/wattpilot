package com.wattpilot.ev.service;

import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import com.wattpilot.ev.dto.LinkVehicleConnectionRequest;
import com.wattpilot.ev.dto.VehicleConnectionResponse;
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
                properties, remover);
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
        when(vehicleConnectionRepository.findByEvIdAndUserId(EV_ID, USER_ID)).thenReturn(Optional.of(connection));

        service.disconnect(USER_ID, EV_ID);

        verify(remover).remove(connection);
    }

    @Test
    void disconnectOnAnUnlinkedEvIsNotFound() {
        VehicleConnectionService service = service(true);
        when(vehicleConnectionRepository.findByEvIdAndUserId(EV_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.disconnect(USER_ID, EV_ID))
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
