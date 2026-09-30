package com.wattpilot.ev.repository;

import com.wattpilot.ev.entity.VehicleConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VehicleConnectionRepository extends JpaRepository<VehicleConnection, Long> {

    Optional<VehicleConnection> findByEvId(Long evId);

    Optional<VehicleConnection> findByEvIdAndUserId(Long evId, Long userId);

    boolean existsByEvId(Long evId);

    boolean existsByUserIdAndSmartcarVehicleId(Long userId, String smartcarVehicleId);

    void deleteByEvId(Long evId);
}
