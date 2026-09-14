package com.wattpilot.dashboard.service;

import com.wattpilot.charging.dto.ChargingCandidate;
import com.wattpilot.charging.dto.EvSnapshot;
import com.wattpilot.charging.entity.ChargingPlan;
import com.wattpilot.charging.entity.ChargingSchedule;
import com.wattpilot.charging.entity.ChargingScheduleStatus;
import com.wattpilot.charging.entity.ChargingSessionStatus;
import com.wattpilot.charging.repository.ChargingPlanRepository;
import com.wattpilot.charging.repository.ChargingScheduleRepository;
import com.wattpilot.common.PriceArea;
import com.wattpilot.dashboard.dto.DashboardResponse;
import com.wattpilot.dashboard.repository.ChargingAggregateRow;
import com.wattpilot.dashboard.repository.DailyChargingRow;
import com.wattpilot.dashboard.repository.DashboardRepository;
import com.wattpilot.dashboard.repository.RecentSessionRow;
import com.wattpilot.electricity.dto.ElectricityPriceResponse;
import com.wattpilot.electricity.entity.PriceProvider;
import com.wattpilot.electricity.service.ElectricityPriceService;
import com.wattpilot.user.entity.User;
import com.wattpilot.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final Long USER_ID = 1L;
    // Fixed at noon UTC on 2026-09-14 so "today" in Europe/Oslo (CEST, +02:00) is unambiguous.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-14T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 14);

    @Mock private DashboardRepository dashboardRepository;
    @Mock private ChargingScheduleRepository scheduleRepository;
    @Mock private ChargingPlanRepository planRepository;
    @Mock private ElectricityPriceService electricityPriceService;
    @Mock private UserService userService;

    private DashboardService service() {
        return new DashboardService(dashboardRepository, scheduleRepository, planRepository,
                electricityPriceService, userService, CLOCK);
    }

    private void stubEmptyAggregates() {
        lenient().when(dashboardRepository.summarizeAllTime(eq(USER_ID), eq(ChargingSessionStatus.COMPLETED)))
                .thenReturn(new ChargingAggregateRow(0L, null, null, null));
        lenient().when(dashboardRepository.summarizeSince(eq(USER_ID), eq(ChargingSessionStatus.COMPLETED), any()))
                .thenReturn(new ChargingAggregateRow(0L, null, null, null));
        lenient().when(dashboardRepository.findCompletedSince(eq(USER_ID), eq(ChargingSessionStatus.COMPLETED), any()))
                .thenReturn(List.of());
        lenient().when(dashboardRepository.findRecentCompleted(eq(USER_ID), eq(ChargingSessionStatus.COMPLETED), any(Limit.class)))
                .thenReturn(List.of());
        lenient().when(planRepository.findIdsByUserId(USER_ID)).thenReturn(List.of());
        lenient().when(userService.getById(USER_ID)).thenReturn(user(PriceArea.NO1));
        lenient().when(electricityPriceService.findCurrentPrice(any())).thenReturn(Optional.empty());
    }

    @Test
    void anAccountWithNoHistoryGetsZeroedBlocksAndAThirtyDayZeroFilledTrend() {
        stubEmptyAggregates();

        DashboardResponse response = service().getDashboard(USER_ID);

        assertThat(response.summary().totalSessions()).isZero();
        assertThat(response.summary().totalEnergyKwh()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.summary().totalSavingsNok()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.summary().averageCostPerKwh()).isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(response.nextCharging()).isNull();
        assertThat(response.currentPrice()).isNull();

        assertThat(response.costComparison().baselineCostNok()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.costComparison().savingsPercent()).isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(response.savingsTrend()).hasSize(30);
        assertThat(response.savingsTrend().get(0).date()).isEqualTo(TODAY.minusDays(29));
        assertThat(response.savingsTrend().get(29).date()).isEqualTo(TODAY);
        assertThat(response.savingsTrend()).allSatisfy(point ->
                assertThat(point.savingsNok()).isEqualByComparingTo(BigDecimal.ZERO));

        assertThat(response.recentSessions()).isEmpty();
    }

    @Test
    void summaryUsesRealizedSavingsAndEnergyWeightedAverageCost() {
        stubEmptyAggregates();
        when(dashboardRepository.summarizeAllTime(eq(USER_ID), eq(ChargingSessionStatus.COMPLETED)))
                .thenReturn(new ChargingAggregateRow(12L, new BigDecimal("186.40"),
                        new BigDecimal("389.80"), new BigDecimal("265.00")));

        DashboardResponse response = service().getDashboard(USER_ID);

        assertThat(response.summary().totalSessions()).isEqualTo(12L);
        assertThat(response.summary().totalEnergyKwh()).isEqualByComparingTo("186.40");
        // baseline - actual = 389.80 - 265.00
        assertThat(response.summary().totalSavingsNok()).isEqualByComparingTo("124.80");
        // actual / energy = 265.00 / 186.40
        assertThat(response.summary().averageCostPerKwh()).isEqualByComparingTo(
                new BigDecimal("265.00").divide(new BigDecimal("186.40"), 4, java.math.RoundingMode.HALF_UP));
    }

    @Test
    void nextChargingPrefersInProgressOverWaitingRegardlessOfListOrder() {
        stubEmptyAggregates();
        when(planRepository.findIdsByUserId(USER_ID)).thenReturn(List.of(10L, 20L));

        ChargingSchedule waiting = withId(ChargingSchedule.create(10L, at(2), at(3),
                new BigDecimal("5.00"), new BigDecimal("1.00")), 100L);
        ChargingSchedule inProgress = withId(ChargingSchedule.create(20L, at(0), at(1),
                new BigDecimal("7.75"), new BigDecimal("2.05")), 200L);
        inProgress.markInProgress();
        // Deliberately returned WAITING-first to prove the preference is explicit, not incidental order.
        when(scheduleRepository.findByPlanIdsAndStatusInOrderByScheduledStartAt(eq(List.of(10L, 20L)), any()))
                .thenReturn(List.of(waiting, inProgress));

        ChargingPlan plan = plan(USER_ID, 5L, "Running car");
        when(planRepository.findById(20L)).thenReturn(Optional.of(plan));

        DashboardResponse response = service().getDashboard(USER_ID);

        assertThat(response.nextCharging()).isNotNull();
        assertThat(response.nextCharging().scheduleId()).isEqualTo(200L);
        assertThat(response.nextCharging().status()).isEqualTo(ChargingScheduleStatus.IN_PROGRESS);
        assertThat(response.nextCharging().evName()).isEqualTo("Running car");
    }

    @Test
    void nextChargingIsNullAndScheduleQueryIsSkippedWhenUserHasNoPlans() {
        stubEmptyAggregates();

        DashboardResponse response = service().getDashboard(USER_ID);

        assertThat(response.nextCharging()).isNull();
        verify(scheduleRepository, never()).findByPlanIdsAndStatusInOrderByScheduledStartAt(any(), any());
    }

    @Test
    void currentPriceComputesDifferenceAgainstTodaysAverage() {
        stubEmptyAggregates();
        when(electricityPriceService.findCurrentPrice(PriceArea.NO1))
                .thenReturn(Optional.of(priceResponse(new BigDecimal("1.24"))));
        when(electricityPriceService.getAveragePrice(eq(PriceArea.NO1), eq(TODAY)))
                .thenReturn(Optional.of(new BigDecimal("1.87")));

        DashboardResponse response = service().getDashboard(USER_ID);

        assertThat(response.currentPrice()).isNotNull();
        assertThat(response.currentPrice().priceNokPerKwh()).isEqualByComparingTo("1.24");
        assertThat(response.currentPrice().todayAveragePriceNokPerKwh()).isEqualByComparingTo("1.87");
        // (1.24 - 1.87) / 1.87 * 100, rounded to 2dp.
        assertThat(response.currentPrice().differencePercent()).isEqualByComparingTo("-33.69");
    }

    @Test
    void currentPriceIsNullAndAverageIsNeverQueriedWhenNoIntervalCoversNow() {
        stubEmptyAggregates();

        DashboardResponse response = service().getDashboard(USER_ID);

        assertThat(response.currentPrice()).isNull();
        verify(electricityPriceService, never()).getAveragePrice(any(), any());
    }

    @Test
    void costComparisonSavingsPercentIsZeroWhenBaselineIsZero() {
        stubEmptyAggregates();
        when(dashboardRepository.summarizeSince(eq(USER_ID), eq(ChargingSessionStatus.COMPLETED), any()))
                .thenReturn(new ChargingAggregateRow(0L, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        DashboardResponse response = service().getDashboard(USER_ID);

        assertThat(response.costComparison().savingsPercent()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void savingsTrendSumsSameDaySessionsAndZeroFillsOtherDays() {
        stubEmptyAggregates();
        OffsetDateTime completedToday1 = TODAY.atTime(8, 0).atZone(java.time.ZoneId.of("Europe/Oslo"))
                .toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime completedToday2 = TODAY.atTime(20, 0).atZone(java.time.ZoneId.of("Europe/Oslo"))
                .toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);
        when(dashboardRepository.findCompletedSince(eq(USER_ID), eq(ChargingSessionStatus.COMPLETED), any()))
                .thenReturn(List.of(
                        new DailyChargingRow(completedToday1, new BigDecimal("10.00"), new BigDecimal("7.00")),
                        new DailyChargingRow(completedToday2, new BigDecimal("5.00"), new BigDecimal("4.20"))));

        DashboardResponse response = service().getDashboard(USER_ID);

        var todayPoint = response.savingsTrend().get(29);
        assertThat(todayPoint.date()).isEqualTo(TODAY);
        // (10.00 - 7.00) + (5.00 - 4.20)
        assertThat(todayPoint.savingsNok()).isEqualByComparingTo("3.80");
        assertThat(response.savingsTrend().get(28).savingsNok()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void recentSessionsDeriveRealizedSavingsFromBaselineMinusActual() {
        stubEmptyAggregates();
        when(dashboardRepository.findRecentCompleted(eq(USER_ID), eq(ChargingSessionStatus.COMPLETED), any(Limit.class)))
                .thenReturn(List.of(new RecentSessionRow(101L, 5L, "Model 3",
                        OffsetDateTime.parse("2026-09-14T05:00:00Z"), OffsetDateTime.parse("2026-09-14T07:00:00Z"),
                        new BigDecimal("18.30"), new BigDecimal("24.20"), new BigDecimal("29.50"))));

        DashboardResponse response = service().getDashboard(USER_ID);

        assertThat(response.recentSessions()).hasSize(1);
        var session = response.recentSessions().get(0);
        assertThat(session.sessionId()).isEqualTo(101L);
        assertThat(session.evName()).isEqualTo("Model 3");
        assertThat(session.realizedSavingsNok()).isEqualByComparingTo("5.30");
    }

    private static User user(PriceArea priceArea) {
        User user = User.register("user@example.com", "hash", "Iris", priceArea);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    private static ElectricityPriceResponse priceResponse(BigDecimal pricePerKwh) {
        return new ElectricityPriceResponse(1L, PriceProvider.HVA_KOSTER_STROMMEN, PriceArea.NO1,
                OffsetDateTime.parse("2026-09-14T12:00:00Z"), OffsetDateTime.parse("2026-09-14T13:00:00Z"),
                pricePerKwh, "NOK", OffsetDateTime.parse("2026-09-14T10:00:00Z"));
    }

    private static ChargingPlan plan(Long userId, Long evId, String evName) {
        ChargingCandidate candidate = new ChargingCandidate(1, at(0), at(1),
                new BigDecimal("7.75"), new BigDecimal("2.05"), new BigDecimal("4.00"),
                new BigDecimal("1.95"), List.of());
        ChargingPlan plan = ChargingPlan.succeeded(userId, evId, PriceArea.NO1,
                new BigDecimal("30"), new BigDecimal("80"), at(0), at(7),
                new EvSnapshot(evName, "Make", "Model", new BigDecimal("60.00"),
                        new BigDecimal("11.00"), new BigDecimal("7.40")),
                new BigDecimal("30.00"), new BigDecimal("7.40"), 153, candidate);
        return withId(plan, 999L);
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    /** An {@code HH:00} wall-clock time on 2026-09-14 in Norwegian summer time (+02:00). */
    private static OffsetDateTime at(int hour) {
        return OffsetDateTime.parse("2026-09-14T%02d:00:00+02:00".formatted(hour));
    }
}
