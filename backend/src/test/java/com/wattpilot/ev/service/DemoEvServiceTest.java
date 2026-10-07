package com.wattpilot.ev.service;

import com.wattpilot.ev.entity.Ev;
import com.wattpilot.ev.entity.EvStatus;
import com.wattpilot.ev.entity.VehicleConnection;
import com.wattpilot.ev.repository.EvRepository;
import com.wattpilot.ev.repository.VehicleConnectionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoEvServiceTest {

    private static final Long TEMPLATE_USER_ID = 1L;
    private static final Long VISITOR_USER_ID = 2L;

    @Mock
    private EvRepository evRepository;

    @Mock
    private VehicleConnectionRepository vehicleConnectionRepository;

    @InjectMocks
    private DemoEvService demoEvService;

    @Test
    void copiesEachTemplateEvAsALockedEvOwnedByTheVisitor() {
        Ev template = templateEv(10L);
        when(evRepository.findByUserIdAndStatusOrderById(TEMPLATE_USER_ID, EvStatus.ACTIVE))
                .thenReturn(List.of(template));
        when(evRepository.save(any(Ev.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(vehicleConnectionRepository.findByEvId(10L)).thenReturn(Optional.empty());

        int copied = demoEvService.copyDemoEvs(TEMPLATE_USER_ID, VISITOR_USER_ID);

        assertThat(copied).isEqualTo(1);
        ArgumentCaptor<Ev> captor = ArgumentCaptor.forClass(Ev.class);
        verify(evRepository).save(captor.capture());
        Ev copy = captor.getValue();
        assertThat(copy.getUserId()).isEqualTo(VISITOR_USER_ID);
        assertThat(copy.isLocked()).isTrue();
        assertThat(copy.getStatus()).isEqualTo(EvStatus.ACTIVE);
        assertThat(copy.getName()).isEqualTo("Demo i4");
        assertThat(copy.getManufacturer()).isEqualTo("BMW");
        assertThat(copy.getModel()).isEqualTo("i4 eDrive40");
        assertThat(copy.getBatteryCapacityKwh()).isEqualByComparingTo("81.10");
        assertThat(copy.getMaxAcChargingPowerKw()).isEqualByComparingTo("11.00");
        assertThat(copy.getDefaultChargerPowerKw()).isEqualByComparingTo("7.40");
        verify(vehicleConnectionRepository, never()).save(any(VehicleConnection.class));
    }

    @Test
    void copiesTheSmartcarConnectionOntoTheNewEvWhenTheTemplateHasOne() {
        Ev template = templateEv(10L);
        when(evRepository.findByUserIdAndStatusOrderById(TEMPLATE_USER_ID, EvStatus.ACTIVE))
                .thenReturn(List.of(template));
        when(evRepository.save(any(Ev.class))).thenAnswer(invocation -> {
            Ev saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 20L);
            return saved;
        });
        when(vehicleConnectionRepository.findByEvId(10L)).thenReturn(Optional.of(
                VehicleConnection.link(TEMPLATE_USER_ID, 10L, "sc-user", "sc-vehicle", "sc-connection",
                        "TESLA", "Model 3", 2022)));

        demoEvService.copyDemoEvs(TEMPLATE_USER_ID, VISITOR_USER_ID);

        ArgumentCaptor<VehicleConnection> captor = ArgumentCaptor.forClass(VehicleConnection.class);
        verify(vehicleConnectionRepository).save(captor.capture());
        VehicleConnection copy = captor.getValue();
        assertThat(copy.getUserId()).isEqualTo(VISITOR_USER_ID);
        assertThat(copy.getEvId()).isEqualTo(20L);
        assertThat(copy.getSmartcarUserId()).isEqualTo("sc-user");
        assertThat(copy.getSmartcarVehicleId()).isEqualTo("sc-vehicle");
        assertThat(copy.getSmartcarConnectionId()).isEqualTo("sc-connection");
        assertThat(copy.getVehicleMake()).isEqualTo("TESLA");
        assertThat(copy.getVehicleModel()).isEqualTo("Model 3");
        assertThat(copy.getVehicleYear()).isEqualTo(2022);
    }

    @Test
    void copiesNothingWhenTheTemplateHasNoActiveEv() {
        when(evRepository.findByUserIdAndStatusOrderById(TEMPLATE_USER_ID, EvStatus.ACTIVE))
                .thenReturn(List.of());

        assertThat(demoEvService.copyDemoEvs(TEMPLATE_USER_ID, VISITOR_USER_ID)).isZero();
        verify(evRepository, never()).save(any(Ev.class));
    }

    private static Ev templateEv(Long id) {
        Ev ev = Ev.register(TEMPLATE_USER_ID, "Demo i4", "BMW", "i4 eDrive40",
                new BigDecimal("81.10"), new BigDecimal("11.00"), new BigDecimal("7.40"));
        ReflectionTestUtils.setField(ev, "id", id);
        return ev;
    }
}
