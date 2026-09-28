package com.wattpilot.ev.service;

import com.wattpilot.ev.dto.VehicleModelResponse;
import com.wattpilot.ev.entity.VehicleModel;
import com.wattpilot.ev.repository.VehicleModelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Read-only lookup over the curated vehicle specification presets (V1.5 master data). There is no
 * write path: rows are seeded by Flyway.
 */
@Service
@Transactional(readOnly = true)
public class VehicleModelService {

    private final VehicleModelRepository vehicleModelRepository;

    public VehicleModelService(VehicleModelRepository vehicleModelRepository) {
        this.vehicleModelRepository = vehicleModelRepository;
    }

    public List<VehicleModelResponse> search(String query) {
        List<VehicleModel> models = StringUtils.hasText(query)
                ? vehicleModelRepository
                        .findByManufacturerContainingIgnoreCaseOrModelContainingIgnoreCaseOrderByManufacturerAscModelAsc(
                                query, query)
                : vehicleModelRepository.findAllByOrderByManufacturerAscModelAsc();
        return models.stream().map(VehicleModelResponse::from).toList();
    }
}
