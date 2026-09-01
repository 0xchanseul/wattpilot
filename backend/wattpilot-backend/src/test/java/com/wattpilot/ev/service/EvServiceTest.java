package com.wattpilot.ev.service;

import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import com.wattpilot.common.response.PageResponse;
import com.wattpilot.ev.dto.CreateEvRequest;
import com.wattpilot.ev.dto.EvResponse;
import com.wattpilot.ev.dto.UpdateEvRequest;
import com.wattpilot.ev.entity.Ev;
import com.wattpilot.ev.entity.EvStatus;
import com.wattpilot.ev.repository.EvRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long EV_ID = 10L;

    @Mock
    private EvRepository evRepository;

    @InjectMocks
    private EvService evService;

    @Test
    void registerTrimsTextFieldsAndStoresAnActiveEvForTheUser() {
        when(evRepository.save(any(Ev.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EvResponse response = evService.register(USER_ID, new CreateEvRequest(
                "  My i4  ", "  BMW  ", "  i4 eDrive40  ",
                new BigDecimal("81.10"), new BigDecimal("11.00"), new BigDecimal("7.40")));

        ArgumentCaptor<Ev> captor = ArgumentCaptor.forClass(Ev.class);
        verify(evRepository).save(captor.capture());
        Ev saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getName()).isEqualTo("My i4");
        assertThat(saved.getManufacturer()).isEqualTo("BMW");
        assertThat(saved.getModel()).isEqualTo("i4 eDrive40");
        assertThat(saved.getStatus()).isEqualTo(EvStatus.ACTIVE);
        assertThat(response.name()).isEqualTo("My i4");
    }

    @Test
    void getOnAnEvOwnedByAnotherUserIsReportedAsNotFound() {
        when(evRepository.findByIdAndUserId(EV_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> evService.get(USER_ID, EV_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.EV_NOT_FOUND);
    }

    @Test
    void updateAppliesOnlyTheProvidedFieldsAndLeavesTheRestUnchanged() {
        Ev ev = activeEv();
        when(evRepository.findByIdAndUserId(EV_ID, USER_ID)).thenReturn(Optional.of(ev));
        when(evRepository.saveAndFlush(any(Ev.class))).thenAnswer(invocation -> invocation.getArgument(0));

        evService.update(USER_ID, EV_ID, new UpdateEvRequest(
                "  Renamed  ", null, null, null, new BigDecimal("9.60"), null, null));

        assertThat(ev.getName()).isEqualTo("Renamed");
        assertThat(ev.getManufacturer()).isEqualTo("BMW");
        assertThat(ev.getMaxAcChargingPowerKw()).isEqualByComparingTo("9.60");
        assertThat(ev.getDefaultChargerPowerKw()).isEqualByComparingTo("7.40");
        assertThat(ev.getStatus()).isEqualTo(EvStatus.ACTIVE);
    }

    @Test
    void updateCanReactivateADeactivatedEv() {
        Ev ev = activeEv();
        ev.deactivate();
        when(evRepository.findByIdAndUserId(EV_ID, USER_ID)).thenReturn(Optional.of(ev));
        when(evRepository.saveAndFlush(any(Ev.class))).thenAnswer(invocation -> invocation.getArgument(0));

        evService.update(USER_ID, EV_ID,
                new UpdateEvRequest(null, null, null, null, null, null, EvStatus.ACTIVE));

        assertThat(ev.getStatus()).isEqualTo(EvStatus.ACTIVE);
    }

    @Test
    void deactivateSetsTheStatusToInactive() {
        Ev ev = activeEv();
        when(evRepository.findByIdAndUserId(EV_ID, USER_ID)).thenReturn(Optional.of(ev));

        evService.deactivate(USER_ID, EV_ID);

        assertThat(ev.getStatus()).isEqualTo(EvStatus.INACTIVE);
    }

    @Test
    void listWithoutAStatusFilterQueriesActiveEvsOnly() {
        Pageable pageable = PageRequest.of(0, 20);
        when(evRepository.findByUserIdAndStatus(USER_ID, EvStatus.ACTIVE, pageable))
                .thenReturn(new PageImpl<>(List.of(activeEv()), pageable, 1));

        PageResponse<EvResponse> page = evService.list(USER_ID, null, pageable);

        assertThat(page.content()).hasSize(1);
        assertThat(page.page().totalElements()).isEqualTo(1);
        verify(evRepository).findByUserIdAndStatus(USER_ID, EvStatus.ACTIVE, pageable);
    }

    private static Ev activeEv() {
        Ev ev = Ev.register(USER_ID, "My i4", "BMW", "i4 eDrive40",
                new BigDecimal("81.10"), new BigDecimal("11.00"), new BigDecimal("7.40"));
        ReflectionTestUtils.setField(ev, "id", EV_ID);
        return ev;
    }
}
