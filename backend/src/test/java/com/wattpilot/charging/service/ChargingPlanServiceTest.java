package com.wattpilot.charging.service;

import com.wattpilot.charging.dto.ChargingPlanResponse;
import com.wattpilot.charging.dto.ChargingPlanSlot;
import com.wattpilot.charging.dto.CreateChargingPlanRequest;
import com.wattpilot.charging.dto.EvSnapshot;
import com.wattpilot.charging.dto.OptimizationResult;
import com.wattpilot.charging.entity.ChargingPlan;
import com.wattpilot.charging.entity.ChargingPlanStatus;
import com.wattpilot.charging.exception.ChargingPlanInfeasibleException;
import com.wattpilot.charging.repository.ChargingPlanRepository;
import com.wattpilot.charging.repository.ChargingPlanSlotRepository;
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
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChargingPlanServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long EV_ID = 10L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-24T16:00:00Z"), ZoneOffset.UTC);

    @Mock
    private ChargingOptimizationService optimizationService;
    @Mock
    private EvService evService;
    @Mock
    private ElectricityPriceService electricityPriceService;
    @Mock
    private ChargingPlanRepository planRepository;
    @Mock
    private ChargingPlanSlotRepository slotRepository;

    private ChargingPlanService service;

    @BeforeEach
    void setUp() {
        service = new ChargingPlanService(optimizationService, evService, electricityPriceService,
                planRepository, slotRepository, CLOCK);
    }

    @Test
    void persistsASucceededPlanWithItsSlotsInCalculationOrder() {
        when(optimizationService.optimize(any())).thenReturn(success());
        when(electricityPriceService.getPricesInWindow(eq(PriceArea.NO1), any(), any()))
                .thenReturn(List.of(price(101L, "20:00", "21:00", "0.50"), price(102L, "21:00", "22:00", "0.40")));
        when(planRepository.save(any())).thenAnswer(invocation -> {
            ChargingPlan plan = invocation.getArgument(0);
            ReflectionTestUtils.setField(plan, "id", 42L);
            return plan;
        });

        ChargingPlanResponse response = service.createPlan(USER_ID, request("25", "60"));

        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.status()).isEqualTo(ChargingPlanStatus.SUCCEEDED);
        assertThat(response.slots()).hasSize(2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<com.wattpilot.charging.entity.ChargingPlanSlot>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(slotRepository).saveAll(captor.capture());
        List<com.wattpilot.charging.entity.ChargingPlanSlot> saved = captor.getValue();
        assertThat(saved).extracting(com.wattpilot.charging.entity.ChargingPlanSlot::getSequenceNo)
                .containsExactly(1, 2);
        assertThat(saved).extracting(com.wattpilot.charging.entity.ChargingPlanSlot::getElectricityPriceId)
                .containsExactly(101L, 102L);
        assertThat(saved).allSatisfy(slot -> assertThat(slot.getChargingPlanId()).isEqualTo(42L));
    }

    @Test
    void persistedSlotSumsStayConsistentWithThePlanAggregates() {
        when(optimizationService.optimize(any())).thenReturn(success());
        when(electricityPriceService.getPricesInWindow(eq(PriceArea.NO1), any(), any()))
                .thenReturn(List.of(price(101L, "20:00", "21:00", "0.50"), price(102L, "21:00", "22:00", "0.40")));
        when(planRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.createPlan(USER_ID, request("25", "60"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<com.wattpilot.charging.entity.ChargingPlanSlot>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(slotRepository).saveAll(captor.capture());
        BigDecimal energySum = captor.getValue().stream()
                .map(com.wattpilot.charging.entity.ChargingPlanSlot::getPlannedEnergyKwh)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal costSum = captor.getValue().stream()
                .map(com.wattpilot.charging.entity.ChargingPlanSlot::getExpectedCostNok)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // success(): slot energies 10.00 + 5.00, slot costs 5.0000 + 2.0000
        assertThat(energySum).isEqualByComparingTo("15.00");
        assertThat(costSum).isEqualByComparingTo("7.0000");
    }

    @Test
    void persistsAFailedPlanWithoutSlotsAndRaisesA422() {
        when(optimizationService.optimize(any())).thenReturn(new OptimizationResult.Infeasible(
                OptimizationResult.Reason.DEADLINE_TOO_SOON, "Not enough time before the deadline."));
        when(evService.getActiveOwnedEv(USER_ID, EV_ID)).thenReturn(ev());
        when(planRepository.save(any())).thenAnswer(invocation -> {
            ChargingPlan plan = invocation.getArgument(0);
            ReflectionTestUtils.setField(plan, "id", 7L);
            return plan;
        });

        assertThatThrownBy(() -> service.createPlan(USER_ID, request("25", "60")))
                .isInstanceOf(ChargingPlanInfeasibleException.class)
                .satisfies(ex -> {
                    ChargingPlanInfeasibleException infeasible = (ChargingPlanInfeasibleException) ex;
                    assertThat(infeasible.chargingPlanId()).isEqualTo(7L);
                    assertThat(infeasible.reason()).isEqualTo(OptimizationResult.Reason.DEADLINE_TOO_SOON);
                });

        ArgumentCaptor<ChargingPlan> captor = ArgumentCaptor.forClass(ChargingPlan.class);
        verify(planRepository).save(captor.capture());
        ChargingPlan failed = captor.getValue();
        assertThat(failed.getStatus()).isEqualTo(ChargingPlanStatus.FAILED);
        assertThat(failed.getFailureReason()).isEqualTo("Not enough time before the deadline.");
        assertThat(failed.getRecommendedStartAt()).isNull();
        assertThat(failed.getEstimatedCostNok()).isNull();
        verify(slotRepository, never()).saveAll(any());
    }

    @Test
    void doesNotPersistWhenTheRequestFailsValidation() {
        when(optimizationService.optimize(any()))
                .thenThrow(new BusinessException(ErrorCode.VALIDATION_ERROR, "targetBatteryPercent must be greater."));

        assertThatThrownBy(() -> service.createPlan(USER_ID, request("60", "60")))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(planRepository, slotRepository);
    }

    private static OptimizationResult.Success success() {
        return new OptimizationResult.Success(
                new EvSnapshot("My EV", "Make", "Model",
                        new BigDecimal("80.00"), new BigDecimal("11.00"), new BigDecimal("10.00")),
                new BigDecimal("13.50"),
                new BigDecimal("10.00"),
                90,
                at("20:00"),
                at("21:30"),
                new BigDecimal("15.00"),
                new BigDecimal("7.0000"),
                new BigDecimal("9.0000"),
                new BigDecimal("2.0000"),
                List.of(
                        new ChargingPlanSlot(at("20:00"), at("21:00"), new BigDecimal("0.50"),
                                new BigDecimal("10.00"), new BigDecimal("5.0000")),
                        new ChargingPlanSlot(at("21:00"), at("21:30"), new BigDecimal("0.40"),
                                new BigDecimal("5.00"), new BigDecimal("2.0000"))));
    }

    private static CreateChargingPlanRequest request(String current, String target) {
        return new CreateChargingPlanRequest(EV_ID, new BigDecimal(current), new BigDecimal(target),
                OffsetDateTime.parse("2026-08-25T04:00:00Z"), null, PriceArea.NO1);
    }

    private static Ev ev() {
        Ev ev = Ev.register(USER_ID, "My EV", "Make", "Model",
                new BigDecimal("80.00"), new BigDecimal("11.00"), new BigDecimal("10.00"));
        ReflectionTestUtils.setField(ev, "id", EV_ID);
        return ev;
    }

    private static ElectricityPrice price(Long id, String startHhmm, String endHhmm, String pricePerKwh) {
        ElectricityPrice price = ElectricityPrice.of(PriceProvider.HVA_KOSTER_STROMMEN, PriceArea.NO1,
                at(startHhmm), at(endHhmm), new BigDecimal(pricePerKwh), "NOK",
                OffsetDateTime.parse("2026-08-24T10:00:00Z"));
        ReflectionTestUtils.setField(price, "id", id);
        return price;
    }

    /** An {@code HH:mm} wall-clock time on 2026-08-24 in Norwegian summer time (+02:00). */
    private static OffsetDateTime at(String hhmm) {
        return OffsetDateTime.parse("2026-08-24T" + hhmm + ":00+02:00");
    }
}
