package com.wattpilot.ev.service;

import com.wattpilot.ev.entity.Ev;
import com.wattpilot.ev.entity.EvStatus;
import com.wattpilot.ev.entity.VehicleConnection;
import com.wattpilot.ev.repository.EvRepository;
import com.wattpilot.ev.repository.VehicleConnectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gives a demo visitor their own copies of the template demo account's EVs.
 *
 * <p>Each copy is a new locked EV. A vehicle connection is copied row for row, so the visitor's EV
 * points at the same Smartcar vehicle as the template: telemetry works without a Connect login, and
 * because the connection id is shared, a locked EV must never be allowed to disconnect.
 */
@Service
public class DemoEvService {

    private final EvRepository evRepository;
    private final VehicleConnectionRepository vehicleConnectionRepository;

    public DemoEvService(EvRepository evRepository, VehicleConnectionRepository vehicleConnectionRepository) {
        this.evRepository = evRepository;
        this.vehicleConnectionRepository = vehicleConnectionRepository;
    }

    /** @return the number of EVs copied */
    @Transactional
    public int copyDemoEvs(Long templateUserId, Long visitorUserId) {
        List<Ev> templates = evRepository.findByUserIdAndStatusOrderById(templateUserId, EvStatus.ACTIVE);
        for (Ev template : templates) {
            Ev copy = evRepository.save(Ev.registerLocked(
                    visitorUserId,
                    template.getName(),
                    template.getManufacturer(),
                    template.getModel(),
                    template.getBatteryCapacityKwh(),
                    template.getMaxAcChargingPowerKw(),
                    template.getDefaultChargerPowerKw()));
            vehicleConnectionRepository.findByEvId(template.getId())
                    .ifPresent(connection -> copyConnection(connection, visitorUserId, copy.getId()));
        }
        return templates.size();
    }

    private void copyConnection(VehicleConnection template, Long visitorUserId, Long evId) {
        vehicleConnectionRepository.save(VehicleConnection.link(
                visitorUserId,
                evId,
                template.getSmartcarUserId(),
                template.getSmartcarVehicleId(),
                template.getSmartcarConnectionId(),
                template.getVehicleMake(),
                template.getVehicleModel(),
                template.getVehicleYear()));
    }
}
