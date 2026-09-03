package com.wattpilot.charging.service;

import com.wattpilot.charging.ChargingProperties;
import com.wattpilot.charging.dto.OptimizationCommand;
import com.wattpilot.charging.dto.OptimizationResult;
import com.wattpilot.common.PriceArea;
import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import com.wattpilot.electricity.dto.PricePoint;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChargingOptimizationServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long EV_ID = 10L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-24T16:00:00Z"), ZoneOffset.UTC);

    @Mock
    private EvService evService;

    @Mock
    private ElectricityPriceService electricityPriceService;

    private ChargingOptimizationService service;

    @BeforeEach
    void setUp() {
        service = new ChargingOptimizationService(evService, electricityPriceService,
                new ChargingWindowCalculator(), new ChargingProperties(new BigDecimal("0.9")), CLOCK);
    }

    @Test
    void convertsBatteryPercentToEnergyUsingTheEvCapacity() {
        when(evService.getActiveOwnedEv(USER_ID, EV_ID)).thenReturn(ev("80.00", "11.00", "10.00"));
        when(electricityPriceService.getPricePointsInWindow(eq(PriceArea.NO1), any(), any()))
                .thenReturn(flatHours("2026-08-24T16:00:00Z", 12, "0.50"));

        OptimizationResult.Success success = success(service.optimize(command(
                new BigDecimal("20"), new BigDecimal("80"), null, "2026-08-25T04:00:00Z")));

        // 80 kWh * (80 - 20) / 100 = 48 kWh; 48 / (10 * 0.9) = 5.333 h -> 320 minutes.
        assertThat(success.calculatedEnergyKwh()).isEqualByComparingTo("48.00");
        assertThat(success.estimatedDurationMinutes()).isEqualTo(320);
    }

    @Test
    void effectiveChargingPowerIsTheLowerOfEvAndChargerAndExcludesEfficiency() {
        when(evService.getActiveOwnedEv(USER_ID, EV_ID)).thenReturn(ev("80.00", "7.40", "11.00"));
        when(electricityPriceService.getPricePointsInWindow(eq(PriceArea.NO1), any(), any()))
                .thenReturn(flatHours("2026-08-24T16:00:00Z", 12, "0.50"));

        OptimizationResult.Success success = success(service.optimize(command(
                new BigDecimal("40"), new BigDecimal("60"), null, "2026-08-25T04:00:00Z")));

        assertThat(success.effectiveChargingPowerKw()).isEqualByComparingTo("7.40");
    }

    @Test
    void queriesPricesForTheWindowBoundedByNowAndTheDeadline() {
        when(evService.getActiveOwnedEv(USER_ID, EV_ID)).thenReturn(ev("80.00", "11.00", "10.00"));
        when(electricityPriceService.getPricePointsInWindow(eq(PriceArea.NO1), any(), any()))
                .thenReturn(flatHours("2026-08-24T16:00:00Z", 12, "0.50"));

        service.optimize(command(new BigDecimal("20"), new BigDecimal("50"), null, "2026-08-24T22:00:00Z"));

        ArgumentCaptor<OffsetDateTime> from = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> to = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(electricityPriceService).getPricePointsInWindow(eq(PriceArea.NO1), from.capture(), to.capture());
        assertThat(from.getValue().toInstant()).isEqualTo(Instant.parse("2026-08-24T16:00:00Z"));
        assertThat(to.getValue().toInstant()).isEqualTo(Instant.parse("2026-08-24T22:00:00Z"));
    }

    @Test
    void clampsAPastEarliestStartUpToNow() {
        when(evService.getActiveOwnedEv(USER_ID, EV_ID)).thenReturn(ev("80.00", "11.00", "10.00"));
        when(electricityPriceService.getPricePointsInWindow(eq(PriceArea.NO1), any(), any()))
                .thenReturn(flatHours("2026-08-24T16:00:00Z", 12, "0.50"));

        service.optimize(command(new BigDecimal("20"), new BigDecimal("50"),
                OffsetDateTime.parse("2026-08-24T10:00:00Z"), "2026-08-24T22:00:00Z"));

        ArgumentCaptor<OffsetDateTime> from = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(electricityPriceService).getPricePointsInWindow(eq(PriceArea.NO1), from.capture(), any());
        assertThat(from.getValue().toInstant()).isEqualTo(Instant.parse("2026-08-24T16:00:00Z"));
    }

    @Test
    void propagatesEvNotFoundFromTheOwnershipCheck() {
        when(evService.getActiveOwnedEv(USER_ID, EV_ID))
                .thenThrow(new BusinessException(ErrorCode.EV_NOT_FOUND));

        assertThatThrownBy(() -> service.optimize(command(
                new BigDecimal("20"), new BigDecimal("80"), null, "2026-08-25T04:00:00Z")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.EV_NOT_FOUND);
    }

    @Test
    void rejectsATargetThatIsNotAboveTheCurrentLevelWithoutTouchingCollaborators() {
        assertThatThrownBy(() -> service.optimize(command(
                new BigDecimal("80"), new BigDecimal("80"), null, "2026-08-25T04:00:00Z")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        verifyNoInteractions(evService, electricityPriceService);
    }

    @Test
    void rejectsADeadlineInThePast() {
        assertThatThrownBy(() -> service.optimize(command(
                new BigDecimal("20"), new BigDecimal("80"), null, "2026-08-24T15:00:00Z")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void returnsInfeasibleWhenNoPricesCoverTheWindow() {
        when(evService.getActiveOwnedEv(USER_ID, EV_ID)).thenReturn(ev("80.00", "11.00", "10.00"));
        when(electricityPriceService.getPricePointsInWindow(eq(PriceArea.NO1), any(), any()))
                .thenReturn(List.of());

        OptimizationResult result = service.optimize(command(
                new BigDecimal("20"), new BigDecimal("80"), null, "2026-08-25T04:00:00Z"));

        assertThat(result).isInstanceOf(OptimizationResult.Infeasible.class);
        assertThat(((OptimizationResult.Infeasible) result).reason())
                .isEqualTo(OptimizationResult.Reason.INSUFFICIENT_PRICE_DATA);
    }

    private static OptimizationResult.Success success(OptimizationResult result) {
        assertThat(result).isInstanceOf(OptimizationResult.Success.class);
        return (OptimizationResult.Success) result;
    }

    private static OptimizationCommand command(BigDecimal current, BigDecimal target,
                                               OffsetDateTime earliestStartAt, String deadline) {
        return new OptimizationCommand(USER_ID, EV_ID, current, target, earliestStartAt,
                OffsetDateTime.parse(deadline), PriceArea.NO1);
    }

    private static Ev ev(String capacityKwh, String maxAcKw, String chargerKw) {
        Ev ev = Ev.register(USER_ID, "My EV", "Make", "Model",
                new BigDecimal(capacityKwh), new BigDecimal(maxAcKw), new BigDecimal(chargerKw));
        ReflectionTestUtils.setField(ev, "id", EV_ID);
        return ev;
    }

    private static List<PricePoint> flatHours(String firstStartInstant, int count, String pricePerKwh) {
        List<PricePoint> points = new ArrayList<>();
        OffsetDateTime cursor = OffsetDateTime.parse(firstStartInstant.replace("Z", "+00:00"));
        for (int i = 0; i < count; i++) {
            OffsetDateTime next = cursor.plusHours(1);
            points.add(new PricePoint(cursor, next, new BigDecimal(pricePerKwh)));
            cursor = next;
        }
        return points;
    }
}
