package com.wattpilot.charging.repository;

import com.wattpilot.charging.entity.ChargingPlanSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ChargingPlanSlotRepository extends JpaRepository<ChargingPlanSlot, Long> {

    List<ChargingPlanSlot> findByChargingPlanIdOrderBySequenceNoAsc(Long chargingPlanId);

    List<ChargingPlanSlot> findByChargingPlanIdInOrderByChargingPlanIdAscSequenceNoAsc(
            Collection<Long> chargingPlanIds);
}
