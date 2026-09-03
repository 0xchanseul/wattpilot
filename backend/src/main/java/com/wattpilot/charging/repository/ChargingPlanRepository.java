package com.wattpilot.charging.repository;

import com.wattpilot.charging.entity.ChargingPlan;
import com.wattpilot.charging.entity.ChargingPlanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChargingPlanRepository extends JpaRepository<ChargingPlan, Long> {

    /**
     * Single-plan lookups are scoped by owner and status: a plan owned by another user, and a FAILED
     * attempt, are both indistinguishable from one that does not exist (see GET /charging-plans/{planId}).
     */
    Optional<ChargingPlan> findByIdAndUserIdAndStatus(Long id, Long userId, ChargingPlanStatus status);

    Page<ChargingPlan> findByUserIdAndStatus(Long userId, ChargingPlanStatus status, Pageable pageable);

    Page<ChargingPlan> findByUserIdAndEvIdAndStatus(Long userId, Long evId, ChargingPlanStatus status,
                                                    Pageable pageable);
}
