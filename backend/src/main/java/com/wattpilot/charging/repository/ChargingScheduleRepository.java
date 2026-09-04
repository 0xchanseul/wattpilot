package com.wattpilot.charging.repository;

import com.wattpilot.charging.entity.ChargingSchedule;
import com.wattpilot.charging.entity.ChargingScheduleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;

public interface ChargingScheduleRepository extends JpaRepository<ChargingSchedule, Long> {

    Page<ChargingSchedule> findByChargingPlanIdIn(Collection<Long> chargingPlanIds, Pageable pageable);

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
