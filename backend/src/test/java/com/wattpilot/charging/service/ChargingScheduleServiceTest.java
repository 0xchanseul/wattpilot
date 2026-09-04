package com.wattpilot.charging.service;

import com.wattpilot.charging.dto.ChargingCandidate;
import com.wattpilot.charging.dto.ChargingCandidatesResult;
import com.wattpilot.charging.dto.ChargingPlanSlot;
import com.wattpilot.charging.dto.ChargingScheduleResponse;
import com.wattpilot.charging.dto.CreateChargingScheduleRequest;
import com.wattpilot.charging.entity.ChargingPlan;
import com.wattpilot.charging.entity.ChargingPlanStatus;
import com.wattpilot.charging.entity.ChargingSchedule;
import com.wattpilot.charging.entity.ChargingScheduleStatus;
import com.wattpilot.charging.repository.ChargingPlanRepository;
import com.wattpilot.charging.repository.ChargingPlanSlotRepository;
import com.wattpilot.charging.repository.ChargingScheduleRepository;
import com.wattpilot.common.PriceArea;
import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import com.wattpilot.electricity.entity.ElectricityPrice;
import com.wattpilot.electricity.entity.PriceProvider;
import com.wattpilot.electricity.service.ElectricityPriceService;
import com.wattpilot.ev.entity.Ev;
import com.wattpilot.ev.service.EvService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChargingScheduleServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long EV_ID = 10L;

    @Mock private ChargingOptimizationService optimizationService;
    @Mock private ChargingCandidateSelector candidateSelector;
    @Mock private EvService evService;
    @Mock private ElectricityPriceService electricityPriceService;
    @Mock private ChargingPlanRepository planRepository;
    @Mock private ChargingPlanSlotRepository slotRepository;
    @Mock private ChargingScheduleRepository scheduleRepository;

    private ChargingScheduleService service;

    @BeforeEach
    void setUp() {
        service = new ChargingScheduleService(optimizationService, candidateSelector, evService,
                electricityPriceService, planRepository, slotRepository, scheduleRepository);
    }

    @Test
    void persistsOnlyTheSelectedCandidateAsPlanSlotsAndSchedule() {
        ChargingCandidate selected = selectedCandidate();
        stubFeasibleCalculationSelecting(selected);
        when(optimizationService.resolveEarliestStart(null))
                .thenReturn(OffsetDateTime.parse("2026-09-04T00:00:00+02:00"));
        when(planRepository.findIdsByUserIdAndEvId(USER_ID, EV_ID)).thenReturn(List.of());
        when(electricityPriceService.getPricesInWindow(eq(PriceArea.NO1), any(), any()))
                .thenReturn(List.of(price(101L, "01:00", "02:00", "0.30"), price(102L, "02:00", "03:00", "0.20")));
        when(planRepository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0), 77L));
        when(slotRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(scheduleRepository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0), 88L));

        ChargingScheduleResponse response = service.createSchedule(USER_ID, request());

        assertThat(response.id()).isEqualTo(88L);
        assertThat(response.planId()).isEqualTo(77L);
        assertThat(response.evId()).isEqualTo(EV_ID);
        assertThat(response.status()).isEqualTo(ChargingScheduleStatus.CREATED);
        assertThat(response.slots()).hasSize(2);
        assertThat(response.estimatedCostNok()).isEqualByComparingTo(selected.estimatedCostNok());
        assertThat(response.expectedSavingsNok()).isEqualByComparingTo(selected.expectedSavingsNok());

        ArgumentCaptor<ChargingPlan> planCaptor = ArgumentCaptor.forClass(ChargingPlan.class);
        verify(planRepository).save(planCaptor.capture());
        assertThat(planCaptor.getValue().getStatus()).isEqualTo(ChargingPlanStatus.SUCCEEDED);
        assertThat(planCaptor.getValue().getRecommendedStartAt()).isEqualTo(selected.recommendedStartAt());

        ArgumentCaptor<ChargingSchedule> scheduleCaptor = ArgumentCaptor.forClass(ChargingSchedule.class);
        verify(scheduleRepository).save(scheduleCaptor.capture());
        assertThat(scheduleCaptor.getValue().getScheduledEndAt()).isEqualTo(selected.recommendedEndAt());
        assertThat(scheduleCaptor.getValue().getEstimatedCostNok()).isEqualByComparingTo(selected.estimatedCostNok());
    }

    @Test
    void rejectsWhenTheEvAlreadyHasAnOverlappingActiveSchedule() {
        ChargingCandidate selected = selectedCandidate();
        stubFeasibleCalculationSelecting(selected);
        when(planRepository.findIdsByUserIdAndEvId(USER_ID, EV_ID)).thenReturn(List.of(5L));
        when(scheduleRepository.existsActiveOverlap(any(), any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.createSchedule(USER_ID, request()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.CHARGING_SCHEDULE_CONFLICT);

        verify(planRepository, never()).save(any());
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void propagatesAStaleCandidateSelectionWithoutPersisting() {
        when(evService.getActiveOwnedEvForUpdate(USER_ID, EV_ID)).thenReturn(ev());
        when(optimizationService.calculateCandidates(any(), any())).thenReturn(feasible(selectedCandidate()));
        when(candidateSelector.select(any(), any(), any())).thenThrow(
                new BusinessException(ErrorCode.CHARGING_CANDIDATE_UNAVAILABLE, "gone"));

        assertThatThrownBy(() -> service.createSchedule(USER_ID, request()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.CHARGING_CANDIDATE_UNAVAILABLE);

        verify(planRepository, never()).save(any());
    }

    @Test
    void mapsAnInfeasibleRecalculationOnto422() {
        when(evService.getActiveOwnedEvForUpdate(USER_ID, EV_ID)).thenReturn(ev());
        when(optimizationService.calculateCandidates(any(), any())).thenReturn(new ChargingCandidatesResult.Infeasible(
                com.wattpilot.charging.dto.OptimizationResult.Reason.DEADLINE_TOO_SOON, "too late"));

        assertThatThrownBy(() -> service.createSchedule(USER_ID, request()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.CHARGING_DEADLINE_TOO_SOON);

        verify(planRepository, never()).save(any());
    }

    private void stubFeasibleCalculationSelecting(ChargingCandidate selected) {
        when(evService.getActiveOwnedEvForUpdate(USER_ID, EV_ID)).thenReturn(ev());
        when(optimizationService.calculateCandidates(any(), any())).thenReturn(feasible(selected));
        when(candidateSelector.select(any(), any(), any())).thenReturn(selected);
    }

    private static ChargingCandidatesResult.Feasible feasible(ChargingCandidate selected) {
        return new ChargingCandidatesResult.Feasible(new BigDecimal("30.00"), new BigDecimal("7.40"), 153,
                List.of(new ChargingCandidate(1, at("00:00"), at("02:33"), new BigDecimal("7.75"),
                                new BigDecimal("2.4000"), new BigDecimal("4.0000"), new BigDecimal("1.6000"), List.of()),
                        selected));
    }

    private static ChargingCandidate selectedCandidate() {
        return new ChargingCandidate(2, at("01:00"), at("02:33"), new BigDecimal("7.75"),
                new BigDecimal("2.0500"), new BigDecimal("4.0000"), new BigDecimal("1.9500"),
                List.of(
                        new ChargingPlanSlot(at("01:00"), at("02:00"), new BigDecimal("0.30"),
                                new BigDecimal("5.00"), new BigDecimal("1.5000")),
                        new ChargingPlanSlot(at("02:00"), at("02:33"), new BigDecimal("0.20"),
                                new BigDecimal("2.75"), new BigDecimal("0.5500"))));
    }

    private static CreateChargingScheduleRequest request() {
        return new CreateChargingScheduleRequest(EV_ID, new BigDecimal("30"), new BigDecimal("80"),
                OffsetDateTime.parse("2026-09-04T07:00:00+02:00"), PriceArea.NO1,
                at("01:00"), at("02:33"));
    }

    private static Ev ev() {
        Ev ev = Ev.register(USER_ID, "My EV", "Make", "Model",
                new BigDecimal("60.00"), new BigDecimal("11.00"), new BigDecimal("7.40"));
        ReflectionTestUtils.setField(ev, "id", EV_ID);
        return ev;
    }

    private static ElectricityPrice price(Long id, String startHhmm, String endHhmm, String pricePerKwh) {
        ElectricityPrice price = ElectricityPrice.of(PriceProvider.HVA_KOSTER_STROMMEN, PriceArea.NO1,
                at(startHhmm), at(endHhmm), new BigDecimal(pricePerKwh), "NOK",
                OffsetDateTime.parse("2026-09-03T10:00:00Z"));
        ReflectionTestUtils.setField(price, "id", id);
        return price;
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    /** An {@code HH:mm} wall-clock time on 2026-09-04 in Norwegian summer time (+02:00). */
    private static OffsetDateTime at(String hhmm) {
        return OffsetDateTime.parse("2026-09-04T" + hhmm + ":00+02:00");
    }
}
