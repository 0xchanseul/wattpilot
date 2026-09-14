package com.wattpilot.dashboard.service;

import com.wattpilot.charging.entity.ChargingPlan;
import com.wattpilot.charging.entity.ChargingSchedule;
import com.wattpilot.charging.entity.ChargingScheduleStatus;
import com.wattpilot.charging.entity.ChargingSessionStatus;
import com.wattpilot.charging.repository.ChargingPlanRepository;
import com.wattpilot.charging.repository.ChargingScheduleRepository;
import com.wattpilot.common.PriceArea;
import com.wattpilot.dashboard.dto.CostComparison;
import com.wattpilot.dashboard.dto.CurrentElectricityPrice;
import com.wattpilot.dashboard.dto.DashboardResponse;
import com.wattpilot.dashboard.dto.DashboardSummary;
import com.wattpilot.dashboard.dto.NextCharging;
import com.wattpilot.dashboard.dto.RecentChargingSession;
import com.wattpilot.dashboard.dto.SavingsTrendPoint;
import com.wattpilot.dashboard.repository.ChargingAggregateRow;
import com.wattpilot.dashboard.repository.DailyChargingRow;
import com.wattpilot.dashboard.repository.DashboardRepository;
import com.wattpilot.electricity.service.ElectricityPriceService;
import com.wattpilot.user.entity.User;
import com.wattpilot.user.service.UserService;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the {@code GET /dashboard} aggregate payload from data already owned by other modules — no
 * new table. Reuses {@link DashboardRepository}'s realized-savings join (same convention as
 * {@code ChargingHistoryRepository}), the active-schedule query {@code ChargingScheduleService} uses
 * for its "upcoming"/"in progress" blocks, and {@link ElectricityPriceService} for pricing.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final ZoneId DISPLAY_ZONE = ElectricityPriceService.PRICE_ZONE;
    private static final Set<ChargingScheduleStatus> ACTIVE_SCHEDULE_STATUSES =
            Set.of(ChargingScheduleStatus.WAITING, ChargingScheduleStatus.IN_PROGRESS);
    private static final int TREND_DAYS = 30;
    private static final int RECENT_SESSIONS_LIMIT = 3;

    private final DashboardRepository dashboardRepository;
    private final ChargingScheduleRepository scheduleRepository;
    private final ChargingPlanRepository planRepository;
    private final ElectricityPriceService electricityPriceService;
    private final UserService userService;
    private final Clock clock;

    public DashboardService(DashboardRepository dashboardRepository,
                            ChargingScheduleRepository scheduleRepository,
                            ChargingPlanRepository planRepository,
                            ElectricityPriceService electricityPriceService,
                            UserService userService,
                            Clock clock) {
        this.dashboardRepository = dashboardRepository;
        this.scheduleRepository = scheduleRepository;
        this.planRepository = planRepository;
        this.electricityPriceService = electricityPriceService;
        this.userService = userService;
        this.clock = clock;
    }

    public DashboardResponse getDashboard(Long userId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        LocalDate today = now.atZoneSameInstant(DISPLAY_ZONE).toLocalDate();
        LocalDate trendStartDate = today.minusDays(TREND_DAYS - 1L);
        // Aligned to the same Europe/Oslo calendar-day boundary the trend is zero-filled against, so
        // costComparison and savingsTrend describe exactly the same 30-day window.
        OffsetDateTime trendSince = trendStartDate.atStartOfDay(DISPLAY_ZONE).toOffsetDateTime();

        DashboardSummary summary = DashboardSummary.from(dashboardRepository.summarizeAllTime(
                userId, ChargingSessionStatus.COMPLETED));

        NextCharging nextCharging = findNextCharging(userId);

        User user = userService.getById(userId);
        CurrentElectricityPrice currentPrice = findCurrentPrice(user.getDefaultPriceArea(), now);

        List<SavingsTrendPoint> savingsTrend = buildSavingsTrend(userId, today, trendStartDate, trendSince);

        CostComparison costComparison = CostComparison.from(dashboardRepository.summarizeSince(
                userId, ChargingSessionStatus.COMPLETED, trendSince));

        List<RecentChargingSession> recentSessions = dashboardRepository
                .findRecentCompleted(userId, ChargingSessionStatus.COMPLETED, Limit.of(RECENT_SESSIONS_LIMIT))
                .stream()
                .map(RecentChargingSession::from)
                .toList();

        return new DashboardResponse(summary, nextCharging, currentPrice, savingsTrend, costComparison, recentSessions);
    }

    /**
     * The {@code IN_PROGRESS} schedule if one is currently charging, otherwise the earliest-starting
     * {@code WAITING} one. Reuses the same active-schedule query the Schedules overview uses; the
     * preference is applied explicitly here rather than relied on incidentally via sort order.
     */
    private NextCharging findNextCharging(Long userId) {
        List<Long> planIds = planRepository.findIdsByUserId(userId);
        if (planIds.isEmpty()) {
            return null;
        }

        List<ChargingSchedule> active = scheduleRepository
                .findByPlanIdsAndStatusInOrderByScheduledStartAt(planIds, ACTIVE_SCHEDULE_STATUSES);
        ChargingSchedule chosen = active.stream()
                .filter(schedule -> schedule.getStatus() == ChargingScheduleStatus.IN_PROGRESS)
                .findFirst()
                .or(() -> active.stream().findFirst())
                .orElse(null);
        if (chosen == null) {
            return null;
        }

        ChargingPlan plan = planRepository.findById(chosen.getChargingPlanId()).orElseThrow(
                () -> new IllegalStateException("Charging plan not found for schedule id=" + chosen.getId()));
        return NextCharging.of(chosen, plan);
    }

    /** {@code null} when no stored price interval covers the current instant. */
    private CurrentElectricityPrice findCurrentPrice(PriceArea priceArea, OffsetDateTime now) {
        return electricityPriceService.findCurrentPrice(priceArea)
                .map(current -> {
                    LocalDate today = now.atZoneSameInstant(DISPLAY_ZONE).toLocalDate();
                    BigDecimal average = electricityPriceService.getAveragePrice(priceArea, today).orElse(null);
                    return CurrentElectricityPrice.of(current.pricePerKwh(), average);
                })
                .orElse(null);
    }

    /**
     * The last {@link #TREND_DAYS} Europe/Oslo calendar days, zero-filled, ascending. Bucketing happens
     * here in Java (not in the query) because a timezone-aware {@code GROUP BY date} has no portable
     * JPQL expression, and a single user's 30-day session volume is small.
     */
    private List<SavingsTrendPoint> buildSavingsTrend(Long userId, LocalDate today, LocalDate startDate,
                                                       OffsetDateTime since) {
        List<DailyChargingRow> rows = dashboardRepository.findCompletedSince(userId, ChargingSessionStatus.COMPLETED, since);

        Map<LocalDate, BigDecimal> savingsByDate = new LinkedHashMap<>();
        for (DailyChargingRow row : rows) {
            LocalDate date = row.completedAt().atZoneSameInstant(DISPLAY_ZONE).toLocalDate();
            BigDecimal savings = row.baselineCostNok().subtract(row.actualCostNok());
            savingsByDate.merge(date, savings, BigDecimal::add);
        }

        return startDate.datesUntil(today.plusDays(1))
                .map(date -> new SavingsTrendPoint(date, savingsByDate.getOrDefault(date, BigDecimal.ZERO)))
                .toList();
    }
}
