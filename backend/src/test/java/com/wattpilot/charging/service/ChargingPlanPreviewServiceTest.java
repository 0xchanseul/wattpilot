package com.wattpilot.charging.service;

import com.wattpilot.charging.dto.ChargingCandidate;
import com.wattpilot.charging.dto.ChargingCandidatesResult;
import com.wattpilot.charging.dto.ChargingPlanPreviewResponse;
import com.wattpilot.charging.dto.CreateChargingPlanPreviewRequest;
import com.wattpilot.charging.dto.OptimizationCommand;
import com.wattpilot.charging.dto.OptimizationResult;
import com.wattpilot.common.PriceArea;
import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChargingPlanPreviewServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long EV_ID = 10L;

    @Mock
    private ChargingOptimizationService optimizationService;

    private ChargingPlanPreviewService service;

    @BeforeEach
    void setUp() {
        service = new ChargingPlanPreviewService(optimizationService);
    }

    @Test
    void returnsAtMostThreeCandidatesInCostOrder() {
        when(optimizationService.calculateCandidates(any())).thenReturn(feasible(5));

        ChargingPlanPreviewResponse response = service.preview(USER_ID, request());

        assertThat(response.candidates()).extracting(ChargingCandidate::rank).containsExactly(1, 2, 3);
        assertThat(response.calculatedEnergyKwh()).isEqualByComparingTo("45.00");
        assertThat(response.estimatedDurationMinutes()).isEqualTo(273);
        assertThat(response.evId()).isEqualTo(EV_ID);
        assertThat(response.priceArea()).isEqualTo(PriceArea.NO1);
    }

    @Test
    void returnsFewerThanThreeWhenOnlyOneOrTwoWindowsExist() {
        when(optimizationService.calculateCandidates(any())).thenReturn(feasible(2));

        assertThat(service.preview(USER_ID, request()).candidates()).hasSize(2);
    }

    @Test
    void mapsAnInfeasibleResultOntoTheMatching422Code() {
        when(optimizationService.calculateCandidates(any())).thenReturn(new ChargingCandidatesResult.Infeasible(
                OptimizationResult.Reason.INSUFFICIENT_PRICE_DATA, "no prices"));

        assertThatThrownBy(() -> service.preview(USER_ID, request()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.CHARGING_PRICE_DATA_INSUFFICIENT);
    }

    private static CreateChargingPlanPreviewRequest request() {
        return new CreateChargingPlanPreviewRequest(EV_ID, new BigDecimal("30"), new BigDecimal("80"),
                OffsetDateTime.parse("2026-09-04T07:00:00+02:00"), PriceArea.NO1);
    }

    private static ChargingCandidatesResult.Feasible feasible(int windowCount) {
        List<ChargingCandidate> candidates = new ArrayList<>();
        OffsetDateTime start = OffsetDateTime.parse("2026-09-04T01:00:00+02:00");
        for (int i = 0; i < windowCount; i++) {
            candidates.add(new ChargingCandidate(
                    i + 1,
                    start.plusHours(i),
                    start.plusHours(i).plusMinutes(273),
                    new BigDecimal("50.00"),
                    new BigDecimal(35 + i),
                    new BigDecimal("46.00"),
                    new BigDecimal(11 - i),
                    List.of()));
        }
        return new ChargingCandidatesResult.Feasible(
                new BigDecimal("45.00"), new BigDecimal("7.40"), 273, candidates);
    }
}
