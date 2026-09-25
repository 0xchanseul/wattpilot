package com.wattpilot.charging.service;

import com.wattpilot.charging.entity.ChargingPlan;
import com.wattpilot.charging.entity.ChargingPlanStatus;
import com.wattpilot.charging.repository.ChargingPlanRepository;
import com.wattpilot.charging.repository.ChargingPlanSlotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChargingPlanServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long EV_ID = 10L;

    @Mock
    private ChargingPlanRepository planRepository;

    @Mock
    private ChargingPlanSlotRepository slotRepository;

    @InjectMocks
    private ChargingPlanService service;

    @Test
    void listReplacesAClientSuppliedSortWithNewestFirst() {
        when(planRepository.findByUserIdAndStatus(eq(USER_ID), eq(ChargingPlanStatus.SUCCEEDED), any()))
                .thenReturn(Page.<ChargingPlan>empty());
        when(slotRepository.findByChargingPlanIdInOrderByChargingPlanIdAscSequenceNoAsc(any()))
                .thenReturn(List.of());

        service.listPlans(USER_ID, null, null, PageRequest.of(2, 5, Sort.by("noSuchProperty").ascending()));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(planRepository).findByUserIdAndStatus(eq(USER_ID), eq(ChargingPlanStatus.SUCCEEDED), pageable.capture());
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void evScopedListAlsoUsesNewestFirst() {
        when(planRepository.findByUserIdAndEvIdAndStatus(eq(USER_ID), eq(EV_ID), eq(ChargingPlanStatus.SUCCEEDED), any()))
                .thenReturn(Page.<ChargingPlan>empty());
        when(slotRepository.findByChargingPlanIdInOrderByChargingPlanIdAscSequenceNoAsc(any()))
                .thenReturn(List.of());

        service.listPlans(USER_ID, EV_ID, null, PageRequest.of(0, 20, Sort.by("noSuchProperty")));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(planRepository).findByUserIdAndEvIdAndStatus(
                eq(USER_ID), eq(EV_ID), eq(ChargingPlanStatus.SUCCEEDED), pageable.capture());
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
