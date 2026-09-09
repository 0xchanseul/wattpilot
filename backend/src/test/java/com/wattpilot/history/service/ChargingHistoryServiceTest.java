package com.wattpilot.history.service;

import com.wattpilot.charging.entity.ChargingFailureCode;
import com.wattpilot.charging.entity.ChargingSession;
import com.wattpilot.charging.entity.ChargingSessionStatus;
import com.wattpilot.charging.repository.ChargingPlanRepository;
import com.wattpilot.charging.repository.ChargingPlanSlotRepository;
import com.wattpilot.charging.repository.ChargingScheduleRepository;
import com.wattpilot.charging.repository.ChargingSessionRepository;
import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import com.wattpilot.history.dto.ChargingHistoryItem;
import com.wattpilot.history.dto.ChargingHistoryListResponse;
import com.wattpilot.history.repository.ChargingHistoryRepository;
import com.wattpilot.history.repository.ChargingHistoryRow;
import com.wattpilot.history.repository.ChargingHistorySummaryRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChargingHistoryServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long EV_ID = 7L;

    @Mock private ChargingHistoryRepository historyRepository;
    @Mock private ChargingSessionRepository sessionRepository;
    @Mock private ChargingScheduleRepository scheduleRepository;
    @Mock private ChargingPlanRepository planRepository;
    @Mock private ChargingPlanSlotRepository slotRepository;

    private ChargingHistoryService service() {
        return new ChargingHistoryService(historyRepository, sessionRepository, scheduleRepository,
                planRepository, slotRepository);
    }

    private void stubEmptySummary() {
        lenient().when(historyRepository.summarize(eq(USER_ID), any(), eq(ChargingSessionStatus.COMPLETED)))
                .thenReturn(new ChargingHistorySummaryRow(0L, 0L, null, null));
        lenient().when(historyRepository.summarizeByEv(eq(USER_ID), eq(EV_ID), any(), eq(ChargingSessionStatus.COMPLETED)))
                .thenReturn(new ChargingHistorySummaryRow(0L, 0L, null, null));
    }

    @Test
    void listsBothTerminalStatusesWhenNoStatusFilterIsGiven() {
        stubEmptySummary();
        when(historyRepository.findHistory(eq(USER_ID), any(), any())).thenReturn(Page.empty());

        service().listHistory(USER_ID, null, null, PageRequest.of(0, 20));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChargingSessionStatus>> statuses = ArgumentCaptor.forClass(List.class);
        verify(historyRepository).findHistory(eq(USER_ID), statuses.capture(), any());
        assertThat(statuses.getValue())
                .containsExactlyInAnyOrder(ChargingSessionStatus.COMPLETED, ChargingSessionStatus.FAILED);
    }

    @Test
    void narrowsToASingleStatusWhenFilteredToOneTerminalStatus() {
        stubEmptySummary();
        when(historyRepository.findHistory(eq(USER_ID), any(), any())).thenReturn(Page.empty());

        service().listHistory(USER_ID, null, ChargingSessionStatus.FAILED, PageRequest.of(0, 20));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChargingSessionStatus>> statuses = ArgumentCaptor.forClass(List.class);
        verify(historyRepository).findHistory(eq(USER_ID), statuses.capture(), any());
        assertThat(statuses.getValue()).containsExactly(ChargingSessionStatus.FAILED);
    }

    @Test
    void returnsSummaryButNoItemsForANonHistoryStatusWithoutQueryingRows() {
        stubEmptySummary();

        ChargingHistoryListResponse result =
                service().listHistory(USER_ID, null, ChargingSessionStatus.STARTED, PageRequest.of(0, 20));

        assertThat(result.content()).isEmpty();
        assertThat(result.summary()).isNotNull();
        verify(historyRepository, never()).findHistory(any(), any(), any());
    }

    @Test
    void routesToTheEvScopedQueryAndSummaryWhenAnEvIdIsGiven() {
        stubEmptySummary();
        when(historyRepository.findHistoryByEv(eq(USER_ID), eq(EV_ID), any(), any())).thenReturn(Page.empty());

        service().listHistory(USER_ID, EV_ID, null, PageRequest.of(0, 20));

        verify(historyRepository).summarizeByEv(eq(USER_ID), eq(EV_ID), any(), eq(ChargingSessionStatus.COMPLETED));
        verify(historyRepository).findHistoryByEv(eq(USER_ID), eq(EV_ID), any(), any());
        verify(historyRepository, never()).findHistory(any(), any(), any());
    }

    @Test
    void stripsAnyClientSuppliedSortSoTheRepositoryOrderingWins() {
        stubEmptySummary();
        when(historyRepository.findHistory(eq(USER_ID), any(), any())).thenReturn(Page.empty());

        service().listHistory(USER_ID, null, null, PageRequest.of(2, 5, Sort.by("actualCostNok").ascending()));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(historyRepository).findHistory(eq(USER_ID), any(), pageable.capture());
        assertThat(pageable.getValue().getSort().isSorted()).isFalse();
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void summaryUsesRealizedSavingsAndComputesSuccessRate() {
        when(historyRepository.summarize(eq(USER_ID), any(), eq(ChargingSessionStatus.COMPLETED)))
                .thenReturn(new ChargingHistorySummaryRow(4L, 3L, new BigDecimal("62.00"), new BigDecimal("13.5000")));
        when(historyRepository.findHistory(eq(USER_ID), any(), any())).thenReturn(Page.empty());

        var summary = service().listHistory(USER_ID, null, null, PageRequest.of(0, 20)).summary();

        assertThat(summary.totalSessions()).isEqualTo(4);
        assertThat(summary.successRate()).isEqualByComparingTo("75.0");
        assertThat(summary.totalEnergyKwh()).isEqualByComparingTo("62.00");
        assertThat(summary.totalSavingsNok()).isEqualByComparingTo("13.5000");
    }

    @Test
    void mapsRowsToDisplayZoneAndDerivesEstimatedAndRealizedSavings() {
        stubEmptySummary();
        OffsetDateTime startedUtc = OffsetDateTime.of(2026, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC);
        ChargingHistoryRow completed = new ChargingHistoryRow(
                10L, 100L, EV_ID, "Iris i4", ChargingSessionStatus.COMPLETED,
                startedUtc, startedUtc, startedUtc.plusHours(3),
                new BigDecimal("30.00"), new BigDecimal("8.0000"), new BigDecimal("12.5000"),
                new BigDecimal("9.0000"), null, null);
        ChargingHistoryRow failed = new ChargingHistoryRow(
                9L, 99L, EV_ID, "Iris i4", ChargingSessionStatus.FAILED,
                startedUtc.minusHours(2), null, null, null, null, null, null,
                ChargingFailureCode.MISSED_EXECUTION_WINDOW, "The charging window closed before it could start.");
        when(historyRepository.findHistory(eq(USER_ID), any(), any()))
                .thenReturn(new PageImpl<>(List.of(completed, failed)));

        List<ChargingHistoryItem> items =
                service().listHistory(USER_ID, null, null, PageRequest.of(0, 20)).content();

        ChargingHistoryItem first = items.get(0);
        assertThat(first.recordedAt().toInstant()).isEqualTo(startedUtc.toInstant());
        assertThat(first.recordedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(1)); // Europe/Oslo, 15 Jan
        assertThat(first.startedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(1));
        assertThat(first.baselineCostNok()).isEqualByComparingTo("12.5000");
        assertThat(first.optimizedCostNok()).isEqualByComparingTo("9.0000");
        assertThat(first.estimatedSavingsNok()).isEqualByComparingTo("3.5000"); // baseline - optimized
        assertThat(first.actualCostNok()).isEqualByComparingTo("8.0000");
        assertThat(first.realizedSavingsNok()).isEqualByComparingTo("4.5000"); // baseline - actual

        ChargingHistoryItem second = items.get(1);
        assertThat(second.status()).isEqualTo(ChargingSessionStatus.FAILED);
        // A missed-window failure has no startedAt/completedAt, but recordedAt is always present.
        assertThat(second.recordedAt()).isNotNull();
        assertThat(second.startedAt()).isNull();
        assertThat(second.completedAt()).isNull();
        assertThat(second.baselineCostNok()).isNull();
        assertThat(second.estimatedSavingsNok()).isNull();
        assertThat(second.realizedSavingsNok()).isNull();
        assertThat(second.failureCode()).isEqualTo(ChargingFailureCode.MISSED_EXECUTION_WINDOW);
    }

    @Test
    void detailReportsNotFoundForAnUnknownOrStillRunningSession() {
        when(sessionRepository.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().getHistoryDetail(USER_ID, 404L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CHARGING_HISTORY_NOT_FOUND));

        ChargingSession running = ChargingSession.started(50L, OffsetDateTime.now());
        when(sessionRepository.findById(11L)).thenReturn(Optional.of(running));
        assertThatThrownBy(() -> service().getHistoryDetail(USER_ID, 11L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CHARGING_HISTORY_NOT_FOUND));
    }
}
