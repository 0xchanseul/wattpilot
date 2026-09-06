package com.wattpilot.charging.repository;

import com.wattpilot.charging.entity.ChargingSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChargingSessionRepository extends JpaRepository<ChargingSession, Long> {

    /** At most one row per schedule; enforced by the uq_charging_sessions_schedule unique constraint. */
    Optional<ChargingSession> findByChargingScheduleId(Long chargingScheduleId);

    List<ChargingSession> findByChargingScheduleIdIn(Collection<Long> chargingScheduleIds);
}
