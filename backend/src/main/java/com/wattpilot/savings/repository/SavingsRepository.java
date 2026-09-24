package com.wattpilot.savings.repository;

import com.wattpilot.charging.entity.ChargingSession;
import com.wattpilot.charging.entity.ChargingSessionStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Read-only aggregate queries backing {@code GET /savings/summary} and {@code GET /savings/daily},
 * over the same {@code charging_sessions -> charging_schedules -> charging_plans} join
 * {@link com.wattpilot.dashboard.repository.DashboardRepository} and
 * {@code com.wattpilot.history.repository.ChargingHistoryRepository} use: the plan carries the
 * owning user and {@code evId}, so every query here is user-scoped with no N+1.
 *
 * <p>Savings are always realized figures ({@code baselineCostNok - actualCostNok}), matching the
 * charging-history convention: the plan's own {@code estimated_savings_nok} is never summed here.
 * {@code since}/{@code until} form a half-open {@code [since, until)} instant range; the caller
 * (an inclusive {@code [from, to]} date range) resolves that boundary.
 */
public interface SavingsRepository extends Repository<ChargingSession, Long> {

    @Query("""
            select new com.wattpilot.savings.repository.SavingsAggregateRow(
                count(se), sum(se.actualEnergyKwh), sum(se.baselineCostNok), sum(se.actualCostNok))
            from ChargingSession se, ChargingSchedule sc, ChargingPlan p
            where se.chargingScheduleId = sc.id
              and sc.chargingPlanId = p.id
              and p.userId = :userId
              and se.status = :completedStatus
              and se.completedAt >= :since
              and se.completedAt < :until
            """)
    SavingsAggregateRow summarize(@Param("userId") Long userId,
                                  @Param("completedStatus") ChargingSessionStatus completedStatus,
                                  @Param("since") OffsetDateTime since,
                                  @Param("until") OffsetDateTime until);

    @Query("""
            select new com.wattpilot.savings.repository.SavingsAggregateRow(
                count(se), sum(se.actualEnergyKwh), sum(se.baselineCostNok), sum(se.actualCostNok))
            from ChargingSession se, ChargingSchedule sc, ChargingPlan p
            where se.chargingScheduleId = sc.id
              and sc.chargingPlanId = p.id
              and p.userId = :userId
              and p.evId = :evId
              and se.status = :completedStatus
              and se.completedAt >= :since
              and se.completedAt < :until
            """)
    SavingsAggregateRow summarizeByEv(@Param("userId") Long userId,
                                      @Param("evId") Long evId,
                                      @Param("completedStatus") ChargingSessionStatus completedStatus,
                                      @Param("since") OffsetDateTime since,
                                      @Param("until") OffsetDateTime until);

    @Query("""
            select new com.wattpilot.savings.repository.SavingsSessionRow(
                se.completedAt, se.actualEnergyKwh, se.baselineCostNok, se.actualCostNok)
            from ChargingSession se, ChargingSchedule sc, ChargingPlan p
            where se.chargingScheduleId = sc.id
              and sc.chargingPlanId = p.id
              and p.userId = :userId
              and se.status = :completedStatus
              and se.completedAt >= :since
              and se.completedAt < :until
            order by se.completedAt asc
            """)
    List<SavingsSessionRow> findCompletedInRange(@Param("userId") Long userId,
                                                 @Param("completedStatus") ChargingSessionStatus completedStatus,
                                                 @Param("since") OffsetDateTime since,
                                                 @Param("until") OffsetDateTime until);

    @Query("""
            select new com.wattpilot.savings.repository.SavingsSessionRow(
                se.completedAt, se.actualEnergyKwh, se.baselineCostNok, se.actualCostNok)
            from ChargingSession se, ChargingSchedule sc, ChargingPlan p
            where se.chargingScheduleId = sc.id
              and sc.chargingPlanId = p.id
              and p.userId = :userId
              and p.evId = :evId
              and se.status = :completedStatus
              and se.completedAt >= :since
              and se.completedAt < :until
            order by se.completedAt asc
            """)
    List<SavingsSessionRow> findCompletedInRangeByEv(@Param("userId") Long userId,
                                                      @Param("evId") Long evId,
                                                      @Param("completedStatus") ChargingSessionStatus completedStatus,
                                                      @Param("since") OffsetDateTime since,
                                                      @Param("until") OffsetDateTime until);
}
