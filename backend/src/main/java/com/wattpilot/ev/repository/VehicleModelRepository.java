package com.wattpilot.ev.repository;

import com.wattpilot.ev.entity.VehicleModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VehicleModelRepository extends JpaRepository<VehicleModel, Long> {

    /**
     * Case-insensitive match against either manufacturer or model, ordered for a stable,
     * predictable picker list.
     */
    List<VehicleModel> findByManufacturerContainingIgnoreCaseOrModelContainingIgnoreCaseOrderByManufacturerAscModelAsc(
            String manufacturerQuery, String modelQuery);

    List<VehicleModel> findAllByOrderByManufacturerAscModelAsc();
}
