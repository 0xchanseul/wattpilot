package com.wattpilot.charging.repository;

import com.wattpilot.charging.entity.ChargingSchedule;
import com.wattpilot.charging.entity.ChargingScheduleStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChargingScheduleRepository extends JpaRepository<ChargingSchedule, Long> {

    Page<ChargingSchedule> findByChargingPlanIdIn(Collection<Long> chargingPlanIds, Pageable pageable);

    /**
     * Row lock for a state transition (execution attempt or user cancellation), so a concurrent
     * scheduler tick and a concurrent cancel request serialise against each other. Must be called from
     * within a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ChargingSchedule s where s.id = :id")
    Optional<ChargingSchedule> findByIdForUpdate(@Param("id") Long id);

    /**
     * Ids of {@code status} schedules whose start time has arrived but whose window has not yet
     * closed, and which are not currently backing off from a transient error. Ordered so an overload of
     * ready schedules is worked through oldest-start-first across successive scheduler ticks.
     */
    @Query("""
            select s.id from ChargingSchedule s
            where s.status = :status
              and s.scheduledStartAt <= :now
              and s.scheduledEndAt > :now
              and (s.nextRetryAt is null or s.nextRetryAt <= :now)
            order by s.scheduledStartAt asc
            """)
    List<Long> findReadyToStartIds(@Param("status") ChargingScheduleStatus status,
                                   @Param("now") OffsetDateTime now, Limit limit);

    /**
     * Ids of {@code status} schedules whose end time has arrived and which are not currently backing
     * off from a transient error.
     */
    @Query("""
            select s.id from ChargingSchedule s
            where s.status = :status
              and s.scheduledEndAt <= :now
              and (s.nextRetryAt is null or s.nextRetryAt <= :now)
            order by s.scheduledEndAt asc
            """)
    List<Long> findReadyToCompleteIds(@Param("status") ChargingScheduleStatus status,
                                      @Param("now") OffsetDateTime now, Limit limit);

    /**
     * Ids of {@code status} schedules whose window has closed without ever starting. Ignores retry
     * backoff state deliberately: a closed window is a hard deadline regardless of a pending retry.
     */
    @Query("""
            select s.id from ChargingSchedule s
            where s.status = :status
              and s.scheduledEndAt <= :now
            order by s.scheduledEndAt asc
            """)
    List<Long> findMissedIds(@Param("status") ChargingScheduleStatus status,
                             @Param("now") OffsetDateTime now, Limit limit);

    /**
     * Whether any of the given plans (all for one EV) already has an active schedule overlapping
     * {@code [windowStart, windowEnd)}. Two intervals overlap iff {@code start < otherEnd} and
     * {@code end > otherStart}.
     */
    @Query("""
            select case when count(s) > 0 then true else false end
            from ChargingSchedule s
            where s.chargingPlanId in :planIds
              and s.status in :activeStatuses
              and s.scheduledStartAt < :windowEnd
              and s.scheduledEndAt > :windowStart
            """)
    boolean existsActiveOverlap(@Param("planIds") Collection<Long> planIds,
                                @Param("activeStatuses") Collection<ChargingScheduleStatus> activeStatuses,
                                @Param("windowStart") OffsetDateTime windowStart,
                                @Param("windowEnd") OffsetDateTime windowEnd);
}
