package com.wattpilot.history.repository;

import com.wattpilot.charging.entity.ChargingSession;
import com.wattpilot.charging.entity.ChargingSessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

/**
 * Read-only queries over executed charging sessions for the charging-history endpoints.
 *
 * <p>Every query joins {@code charging_sessions -> charging_schedules -> charging_plans}: the plan
 * carries the owning user and the EV-name snapshot taken at confirmation time, so the results are
 * user-scoped, have no N+1, and never read the mutable {@code evs} row (a later EV rename or
 * deactivation does not change history).
 *
 * <p>The list ordering is fixed here ({@code created_at DESC, id DESC}); callers pass a sort-free
 * {@link Pageable}. {@code created_at} is the only timestamp present on every session — a FAILED
 * session has no {@code completed_at}, and a missed one has no {@code started_at}.
 */
public interface ChargingHistoryRepository extends Repository<ChargingSession, Long> {

    @Query(value = """
            select new com.wattpilot.history.repository.ChargingHistoryRow(
                se.id, sc.id, p.evId, p.evName, se.status,
                se.startedAt, se.completedAt,
                se.actualEnergyKwh, se.actualCostNok, se.baselineCostNok, se.optimizedCostNok,
                se.failureCode, se.failureReason)
            from ChargingSession se, ChargingSchedule sc, ChargingPlan p
            where se.chargingScheduleId = sc.id
              and sc.chargingPlanId = p.id
              and p.userId = :userId
              and se.status in :statuses
            order by se.createdAt desc, se.id desc
            """,
            countQuery = """
            select count(se)
            from ChargingSession se, ChargingSchedule sc, ChargingPlan p
            where se.chargingScheduleId = sc.id
              and sc.chargingPlanId = p.id
              and p.userId = :userId
              and se.status in :statuses
            """)
    Page<ChargingHistoryRow> findHistory(@Param("userId") Long userId,
                                         @Param("statuses") Collection<ChargingSessionStatus> statuses,
                                         Pageable pageable);

    @Query(value = """
            select new com.wattpilot.history.repository.ChargingHistoryRow(
                se.id, sc.id, p.evId, p.evName, se.status,
                se.startedAt, se.completedAt,
                se.actualEnergyKwh, se.actualCostNok, se.baselineCostNok, se.optimizedCostNok,
                se.failureCode, se.failureReason)
            from ChargingSession se, ChargingSchedule sc, ChargingPlan p
            where se.chargingScheduleId = sc.id
              and sc.chargingPlanId = p.id
              and p.userId = :userId
              and p.evId = :evId
              and se.status in :statuses
            order by se.createdAt desc, se.id desc
            """,
            countQuery = """
            select count(se)
            from ChargingSession se, ChargingSchedule sc, ChargingPlan p
            where se.chargingScheduleId = sc.id
              and sc.chargingPlanId = p.id
              and p.userId = :userId
              and p.evId = :evId
              and se.status in :statuses
            """)
    Page<ChargingHistoryRow> findHistoryByEv(@Param("userId") Long userId,
                                             @Param("evId") Long evId,
                                             @Param("statuses") Collection<ChargingSessionStatus> statuses,
                                             Pageable pageable);

    /**
     * Header totals for the history list: session counts over {@code COMPLETED} + {@code FAILED},
     * plus energy and realized savings ({@code baseline - actual}) summed over the {@code COMPLETED}
     * rows only. Independent of the list's status filter and pagination.
     */
    @Query("""
            select new com.wattpilot.history.repository.ChargingHistorySummaryRow(
                count(se),
                sum(case when se.status = :completedStatus then 1L else 0L end),
                sum(se.actualEnergyKwh),
                sum(se.baselineCostNok - se.actualCostNok))
            from ChargingSession se, ChargingSchedule sc, ChargingPlan p
            where se.chargingScheduleId = sc.id
              and sc.chargingPlanId = p.id
              and p.userId = :userId
              and se.status in :statuses
            """)
    ChargingHistorySummaryRow summarize(@Param("userId") Long userId,
                                        @Param("statuses") Collection<ChargingSessionStatus> statuses,
                                        @Param("completedStatus") ChargingSessionStatus completedStatus);

    @Query("""
            select new com.wattpilot.history.repository.ChargingHistorySummaryRow(
                count(se),
                sum(case when se.status = :completedStatus then 1L else 0L end),
                sum(se.actualEnergyKwh),
                sum(se.baselineCostNok - se.actualCostNok))
            from ChargingSession se, ChargingSchedule sc, ChargingPlan p
            where se.chargingScheduleId = sc.id
              and sc.chargingPlanId = p.id
              and p.userId = :userId
              and p.evId = :evId
              and se.status in :statuses
            """)
    ChargingHistorySummaryRow summarizeByEv(@Param("userId") Long userId,
                                            @Param("evId") Long evId,
                                            @Param("statuses") Collection<ChargingSessionStatus> statuses,
                                            @Param("completedStatus") ChargingSessionStatus completedStatus);
}
