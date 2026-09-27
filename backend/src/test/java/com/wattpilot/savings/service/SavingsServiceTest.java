package com.wattpilot.savings.service;

import com.wattpilot.charging.entity.ChargingSessionStatus;
import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import com.wattpilot.savings.dto.DailySavings;
import com.wattpilot.savings.dto.Granularity;
import com.wattpilot.savings.dto.PatternGroupBy;
import com.wattpilot.savings.dto.SavingsPatternPoint;
import com.wattpilot.savings.dto.SavingsSummary;
import com.wattpilot.savings.repository.SavingsAggregateRow;
import com.wattpilot.savings.repository.SavingsPatternSlotRow;
import com.wattpilot.savings.repository.SavingsRepository;
import com.wattpilot.savings.repository.SavingsSessionRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SavingsServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private SavingsRepository savingsRepository;

    private SavingsService service() {
        return new SavingsService(savingsRepository);
    }

    @Test
    void summaryRejectsAToBeforeFromWithoutQueryingTheRepository() {
        assertThatThrownBy(() -> service().getSummary(USER_ID, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 1), null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(savingsRepository, never()).summarize(any(), any(), any(), any());
    }

    @Test
    void summaryAllowsAOneDayRangeWhereFromEqualsTo() {
        LocalDate day = LocalDate.of(2026, 9, 10);
        when(savingsRepository.summarize(eq(USER_ID), eq(ChargingSessionStatus.COMPLETED), any(), any()))
                .thenReturn(new SavingsAggregateRow(0L, null, null, null));

        SavingsSummary summary = service().getSummary(USER_ID, day, day, null);

        assertThat(summary.from()).isEqualTo(day);
        assertThat(summary.to()).isEqualTo(day);
        assertThat(summary.completedSessionCount()).isZero();
        assertThat(summary.totalSavingsNok()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void summaryUsesRealizedSavingsAndZeroesOutNullAggregates() {
        when(savingsRepository.summarize(eq(USER_ID), eq(ChargingSessionStatus.COMPLETED), any(), any()))
                .thenReturn(new SavingsAggregateRow(4L, new BigDecimal("40.00"), new BigDecimal("100.00"), new BigDecimal("65.00")));

        SavingsSummary summary = service().getSummary(USER_ID, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null);

        assertThat(summary.completedSessionCount()).isEqualTo(4L);
        assertThat(summary.totalEnergyKwh()).isEqualByComparingTo("40.00");
        assertThat(summary.baselineCostNok()).isEqualByComparingTo("100.00");
        assertThat(summary.optimizedCostNok()).isEqualByComparingTo("65.00");
        // baseline - optimized(actual) = 100.00 - 65.00
        assertThat(summary.totalSavingsNok()).isEqualByComparingTo("35.00");
        // 35 / 100 * 100
        assertThat(summary.savingsRatePercent()).isEqualByComparingTo("35.00");
    }

    @Test
    void summaryQueriesTheEvScopedRepositoryMethodWhenEvIdIsGiven() {
        when(savingsRepository.summarizeByEv(eq(USER_ID), eq(5L), eq(ChargingSessionStatus.COMPLETED), any(), any()))
                .thenReturn(new SavingsAggregateRow(0L, null, null, null));

        SavingsSummary summary = service().getSummary(USER_ID, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 5L);

        assertThat(summary.evId()).isEqualTo(5L);
        verify(savingsRepository, never()).summarize(any(), any(), any(), any());
    }

    @Test
    void dailyZeroFillsEveryDateInRangeWhenThereAreNoSessions() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 5);
        when(savingsRepository.findCompletedInRange(eq(USER_ID), eq(ChargingSessionStatus.COMPLETED), any(), any()))
                .thenReturn(List.of());

        List<DailySavings> daily = service().getDaily(USER_ID, from, to, null, null);

        assertThat(daily).hasSize(5);
        assertThat(daily.get(0).date()).isEqualTo(from);
        assertThat(daily.get(4).date()).isEqualTo(to);
        assertThat(daily).allSatisfy(point -> {
            assertThat(point.sessionCount()).isZero();
            assertThat(point.savingsNok()).isEqualByComparingTo(BigDecimal.ZERO);
        });
    }

    @Test
    void dailySumsSameDaySessionsInOsloTimeAndDefaultsToDailyGranularity() {
        // 22:30 UTC on Sep 9 is already Sep 10 in Europe/Oslo (CEST, +02:00).
        OffsetDateTime lateUtc = OffsetDateTime.of(2026, 9, 9, 22, 30, 0, 0, ZoneOffset.UTC);
        OffsetDateTime sameOsloDayMorning = OffsetDateTime.parse("2026-09-10T06:00:00Z");
        when(savingsRepository.findCompletedInRange(eq(USER_ID), eq(ChargingSessionStatus.COMPLETED), any(), any()))
                .thenReturn(List.of(
                        new SavingsSessionRow(lateUtc, new BigDecimal("10.00"), new BigDecimal("8.00"), new BigDecimal("5.00")),
                        new SavingsSessionRow(sameOsloDayMorning, new BigDecimal("5.00"), new BigDecimal("4.00"), new BigDecimal("3.00"))));

        List<DailySavings> daily = service().getDaily(USER_ID, LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 11), null, Granularity.DAILY);

        DailySavings sep10 = daily.stream().filter(d -> d.date().equals(LocalDate.of(2026, 9, 10))).findFirst().orElseThrow();
        assertThat(sep10.sessionCount()).isEqualTo(2);
        assertThat(sep10.energyKwh()).isEqualByComparingTo("15.00");
        // (8.00 - 5.00) + (4.00 - 3.00)
        assertThat(sep10.savingsNok()).isEqualByComparingTo("4.00");

        DailySavings sep9 = daily.stream().filter(d -> d.date().equals(LocalDate.of(2026, 9, 9))).findFirst().orElseThrow();
        assertThat(sep9.sessionCount()).isZero();
    }

    @Test
    void monthlyGranularityBucketsByFirstOfMonthAndZeroFillsEveryMonthInRange() {
        OffsetDateTime septemberSession = OffsetDateTime.parse("2026-09-15T12:00:00Z");
        when(savingsRepository.findCompletedInRange(eq(USER_ID), eq(ChargingSessionStatus.COMPLETED), any(), any()))
                .thenReturn(List.of(new SavingsSessionRow(septemberSession, new BigDecimal("12.00"),
                        new BigDecimal("20.00"), new BigDecimal("14.00"))));

        List<DailySavings> monthly = service().getDaily(
                USER_ID, LocalDate.of(2026, 8, 20), LocalDate.of(2026, 10, 5), null, Granularity.MONTHLY);

        assertThat(monthly).extracting(DailySavings::date).containsExactly(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1));

        DailySavings september = monthly.get(1);
        assertThat(september.sessionCount()).isEqualTo(1);
        assertThat(september.savingsNok()).isEqualByComparingTo("6.00");

        assertThat(monthly.get(0).sessionCount()).isZero();
        assertThat(monthly.get(2).sessionCount()).isZero();
    }

    @Test
    void dailyQueriesTheEvScopedRepositoryMethodWhenEvIdIsGiven() {
        when(savingsRepository.findCompletedInRangeByEv(eq(USER_ID), eq(7L), eq(ChargingSessionStatus.COMPLETED), any(), any()))
                .thenReturn(List.of());

        service().getDaily(USER_ID, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), 7L, null);

        verify(savingsRepository, never()).findCompletedInRange(any(), any(), any(), any());
    }

    @Test
    void patternsRejectsAToBeforeFromWithoutQueryingTheRepository() {
        assertThatThrownBy(() -> service().getPatterns(
                USER_ID, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 1), null, PatternGroupBy.WEEKDAY))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(savingsRepository, never()).findSlotsInRange(any(), any(), any(), any());
    }

    @Test
    void patternsZeroFillsAllHoursWhenThereAreNoSlots() {
        when(savingsRepository.findSlotsInRange(eq(USER_ID), eq(ChargingSessionStatus.COMPLETED), any(), any()))
                .thenReturn(List.of());

        List<SavingsPatternPoint> patterns = service().getPatterns(
                USER_ID, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5), null, PatternGroupBy.HOUR_OF_DAY);

        assertThat(patterns).extracting(SavingsPatternPoint::bucket)
                .containsExactlyElementsOf(IntStream.range(0, 24).boxed().toList());
        assertThat(patterns).allSatisfy(point -> {
            assertThat(point.sessionCount()).isZero();
            assertThat(point.savingsNok()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(point.savingsRatePercent()).isEqualByComparingTo(BigDecimal.ZERO);
        });
    }

    @Test
    void patternsZeroFillsAllWeekdaysWhenThereAreNoSlots() {
        when(savingsRepository.findSlotsInRange(eq(USER_ID), eq(ChargingSessionStatus.COMPLETED), any(), any()))
                .thenReturn(List.of());

        List<SavingsPatternPoint> patterns = service().getPatterns(
                USER_ID, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5), null, PatternGroupBy.WEEKDAY);

        assertThat(patterns).extracting(SavingsPatternPoint::bucket).containsExactly(1, 2, 3, 4, 5, 6, 7);
    }

    @Test
    void patternsSplitASessionCrossingAnHourBoundaryAcrossBothHourBucketsInsteadOfOnlyItsStartHour() {
        // 20:30Z/21:30Z are 22:30/23:30 in Europe/Oslo (CEST, +02:00) in September - two different hours.
        OffsetDateTime slot1Start = OffsetDateTime.parse("2026-09-10T20:30:00Z");
        OffsetDateTime slot2Start = OffsetDateTime.parse("2026-09-10T21:30:00Z");
        when(savingsRepository.findSlotsInRange(eq(USER_ID), eq(ChargingSessionStatus.COMPLETED), any(), any()))
                .thenReturn(List.of(
                        new SavingsPatternSlotRow(1L, slot1Start, new BigDecimal("6.00"), new BigDecimal("3.0000"),
                                new BigDecimal("10.00"), new BigDecimal("20.0000")),
                        new SavingsPatternSlotRow(1L, slot2Start, new BigDecimal("4.00"), new BigDecimal("2.0000"),
                                new BigDecimal("10.00"), new BigDecimal("20.0000"))));

        List<SavingsPatternPoint> patterns = service().getPatterns(
                USER_ID, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10), null, PatternGroupBy.HOUR_OF_DAY);

        SavingsPatternPoint hour22 = patterns.stream().filter(p -> p.bucket() == 22).findFirst().orElseThrow();
        SavingsPatternPoint hour23 = patterns.stream().filter(p -> p.bucket() == 23).findFirst().orElseThrow();

        // baseline prorated by each slot's share of the session's 10.00 kWh: 20.00 * 6/10 and 20.00 * 4/10.
        assertThat(hour22.sessionCount()).isEqualTo(1);
        assertThat(hour22.energyKwh()).isEqualByComparingTo("6.00");
        assertThat(hour22.baselineCostNok()).isEqualByComparingTo("12.0000");
        assertThat(hour22.savingsNok()).isEqualByComparingTo("9.0000");

        assertThat(hour23.sessionCount()).isEqualTo(1);
        assertThat(hour23.energyKwh()).isEqualByComparingTo("4.00");
        assertThat(hour23.baselineCostNok()).isEqualByComparingTo("8.0000");
        assertThat(hour23.savingsNok()).isEqualByComparingTo("6.0000");

        // No bucket carries the session's whole 20.00 baseline: the split adds back up to it (12 + 8).
        assertThat(patterns.stream().filter(p -> p.sessionCount() > 0)).hasSize(2);
    }

    @Test
    void patternsSplitASessionCrossingMidnightAcrossBothWeekdayBucketsInsteadOfOnlyItsStartDay() {
        // Sep 7, 2026 is a Monday in Europe/Oslo. 20:00Z/22:30Z are 22:00 Monday / 00:30 Tuesday locally (CEST, +02:00).
        OffsetDateTime mondayEvening = OffsetDateTime.parse("2026-09-07T20:00:00Z");
        OffsetDateTime tuesdayJustAfterMidnight = OffsetDateTime.parse("2026-09-07T22:30:00Z");
        when(savingsRepository.findSlotsInRange(eq(USER_ID), eq(ChargingSessionStatus.COMPLETED), any(), any()))
                .thenReturn(List.of(
                        new SavingsPatternSlotRow(2L, mondayEvening, new BigDecimal("5.00"), new BigDecimal("2.5000"),
                                new BigDecimal("8.00"), new BigDecimal("16.0000")),
                        new SavingsPatternSlotRow(2L, tuesdayJustAfterMidnight, new BigDecimal("3.00"), new BigDecimal("1.0000"),
                                new BigDecimal("8.00"), new BigDecimal("16.0000"))));

        List<SavingsPatternPoint> patterns = service().getPatterns(
                USER_ID, LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 7), null, PatternGroupBy.WEEKDAY);

        SavingsPatternPoint monday = patterns.stream().filter(p -> p.bucket() == 1).findFirst().orElseThrow();
        SavingsPatternPoint tuesday = patterns.stream().filter(p -> p.bucket() == 2).findFirst().orElseThrow();

        assertThat(monday.sessionCount()).isEqualTo(1);
        assertThat(monday.baselineCostNok()).isEqualByComparingTo("10.0000"); // 16.00 * 5/8
        assertThat(monday.savingsNok()).isEqualByComparingTo("7.5000");

        assertThat(tuesday.sessionCount()).isEqualTo(1);
        assertThat(tuesday.baselineCostNok()).isEqualByComparingTo("6.0000"); // 16.00 * 3/8
        assertThat(tuesday.savingsNok()).isEqualByComparingTo("5.0000");
    }

    @Test
    void patternsCountsASessionOnceWhenTwoOfItsSlotsLandInTheSameBucket() {
        OffsetDateTime slot1Start = OffsetDateTime.parse("2026-09-10T08:00:00Z"); // hour 10 in Oslo
        OffsetDateTime slot2Start = OffsetDateTime.parse("2026-09-10T08:30:00Z"); // same Oslo hour
        when(savingsRepository.findSlotsInRange(eq(USER_ID), eq(ChargingSessionStatus.COMPLETED), any(), any()))
                .thenReturn(List.of(
                        new SavingsPatternSlotRow(3L, slot1Start, new BigDecimal("2.00"), new BigDecimal("1.0000"),
                                new BigDecimal("4.00"), new BigDecimal("8.0000")),
                        new SavingsPatternSlotRow(3L, slot2Start, new BigDecimal("2.00"), new BigDecimal("1.0000"),
                                new BigDecimal("4.00"), new BigDecimal("8.0000"))));

        List<SavingsPatternPoint> patterns = service().getPatterns(
                USER_ID, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10), null, PatternGroupBy.HOUR_OF_DAY);

        SavingsPatternPoint hour10 = patterns.stream().filter(p -> p.bucket() == 10).findFirst().orElseThrow();
        assertThat(hour10.sessionCount()).isEqualTo(1);
        assertThat(hour10.energyKwh()).isEqualByComparingTo("4.00");
    }

    @Test
    void patternsQueriesTheEvScopedRepositoryMethodWhenEvIdIsGiven() {
        when(savingsRepository.findSlotsInRangeByEv(eq(USER_ID), eq(9L), eq(ChargingSessionStatus.COMPLETED), any(), any()))
                .thenReturn(List.of());

        service().getPatterns(USER_ID, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), 9L, PatternGroupBy.HOUR_OF_DAY);

        verify(savingsRepository, never()).findSlotsInRange(any(), any(), any(), any());
    }
}
