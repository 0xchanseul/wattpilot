package com.wattpilot.dashboard.repository;

import com.wattpilot.charging.entity.ChargingSession;
import com.wattpilot.charging.entity.ChargingSessionStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Read-only aggregate queries backing {@code GET /dashboard}, over the same
 * {@code charging_sessions -> charging_schedules -> charging_plans} join
 * {@link com.wattpilot.history.repository.ChargingHistoryRepository} uses: the plan carries the owning
 * user and the {@code ev_name} snapshot, so every query here is user-scoped with no N+1 and never reads
 * the mutable {@code evs} row.
 *
 * <p>Savings are always realized figures ({@code baselineCostNok - actualCostNok}), matching the
 * charging-history convention documented in {@code docs/tech-stack-architecture.md}: the plan's own
 * {@code estimated_savings_nok} / {@code optimized_cost_nok} are never summed here.
 */
public interface DashboardRepository extends Repository<ChargingSession, Long> {

    /**
     * Realized totals over every {@code COMPLETED} session ever recorded for the user. Backs the
     * Dashboard {@code summary} block.
     */
    @Query("""
            select new com.wattpilot.dashboard.repository.ChargingAggregateRow(
                count(se), sum(se.actualEnergyKwh), sum(se.baselineCostNok), sum(se.actualCostNok))
            from ChargingSession se, ChargingSchedule sc, ChargingPlan p
            where se.chargingScheduleId = sc.id
              and sc.chargingPlanId = p.id
              and p.userId = :userId
              and se.status = :completedStatus
            """)
    ChargingAggregateRow summarizeAllTime(@Param("userId") Long userId,
                                          @Param("completedStatus") ChargingSessionStatus completedStatus);

    /**
     * Realized totals over {@code COMPLETED} sessions whose {@code completedAt} falls on or after
     * {@code since}. Backs the Dashboard {@code costComparison} block (last 30 days).
     */
    @Query("""
            select new com.wattpilot.dashboard.repository.ChargingAggregateRow(
                count(se), sum(se.actualEnergyKwh), sum(se.baselineCostNok), sum(se.actualCostNok))
            from ChargingSession se, ChargingSchedule sc, ChargingPlan p
            where se.chargingScheduleId = sc.id
              and sc.chargingPlanId = p.id
              and p.userId = :userId
              and se.status = :completedStatus
              and se.completedAt >= :since
            """)
    ChargingAggregateRow summarizeSince(@Param("userId") Long userId,
                                        @Param("completedStatus") ChargingSessionStatus completedStatus,
                                        @Param("since") OffsetDateTime since);

    /**
     * One row per {@code COMPLETED} session completed on or after {@code since}, oldest first. Backs
     * the Dashboard {@code savingsTrend} block; day-bucketing happens in {@code DashboardService}.
     */
    @Query("""
            select new com.wattpilot.dashboard.repository.DailyChargingRow(
                se.completedAt, se.baselineCostNok, se.actualCostNok)
            from ChargingSession se, ChargingSchedule sc, ChargingPlan p
            where se.chargingScheduleId = sc.id
              and sc.chargingPlanId = p.id
              and p.userId = :userId
              and se.status = :completedStatus
              and se.completedAt >= :since
            order by se.completedAt asc
            """)
    List<DailyChargingRow> findCompletedSince(@Param("userId") Long userId,
                                              @Param("completedStatus") ChargingSessionStatus completedStatus,
                                              @Param("since") OffsetDateTime since);

    /**
     * The most recently completed sessions for the user, newest first. Backs the Dashboard
     * {@code recentSessions} block.
     */
    @Query("""
            select new com.wattpilot.dashboard.repository.RecentSessionRow(
                se.id, p.evId, p.evName, se.startedAt, se.completedAt,
                se.actualEnergyKwh, se.actualCostNok, se.baselineCostNok)
            from ChargingSession se, ChargingSchedule sc, ChargingPlan p
            where se.chargingScheduleId = sc.id
              and sc.chargingPlanId = p.id
              and p.userId = :userId
              and se.status = :completedStatus
            order by se.completedAt desc, se.id desc
            """)
    List<RecentSessionRow> findRecentCompleted(@Param("userId") Long userId,
                                               @Param("completedStatus") ChargingSessionStatus completedStatus,
                                               Limit limit);
}
