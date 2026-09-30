package com.wattpilot.ev.service;

import com.wattpilot.ev.entity.VehicleConnection;
import com.wattpilot.ev.repository.VehicleConnectionRepository;
import com.wattpilot.integration.smartcar.SmartcarClient;
import com.wattpilot.integration.smartcar.SmartcarProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Removes a {@link VehicleConnection}, used by both {@link VehicleConnectionService#disconnect}
 * (the explicit endpoint) and {@link EvService#deactivate} (so a hidden EV never keeps a live
 * Smartcar link). Package-private: it exists only to be shared by those two without
 * {@code EvService} and {@code VehicleConnectionService} depending on each other in a cycle.
 *
 * <p>The Smartcar-side removal is best-effort: the local row is always deleted regardless of its
 * outcome, so a WattPilot user is never stuck with a link they cannot remove because Smartcar is
 * unreachable.
 */
@Component
class VehicleConnectionRemover {

    private static final Logger log = LoggerFactory.getLogger(VehicleConnectionRemover.class);

    private final VehicleConnectionRepository vehicleConnectionRepository;
    private final SmartcarClient smartcarClient;
    private final SmartcarProperties properties;

    VehicleConnectionRemover(VehicleConnectionRepository vehicleConnectionRepository, SmartcarClient smartcarClient,
                             SmartcarProperties properties) {
        this.vehicleConnectionRepository = vehicleConnectionRepository;
        this.smartcarClient = smartcarClient;
        this.properties = properties;
    }

    void removeIfLinked(Long evId) {
        Optional<VehicleConnection> connection = vehicleConnectionRepository.findByEvId(evId);
        connection.ifPresent(this::remove);
    }

    void remove(VehicleConnection connection) {
        if (properties.enabled()) {
            try {
                smartcarClient.removeConnection(connection.getSmartcarConnectionId());
            } catch (RuntimeException ex) {
                log.warn("Failed to remove Smartcar connection {} for ev {}: {}",
                        connection.getSmartcarConnectionId(), connection.getEvId(), ex.getMessage());
            }
        }
        vehicleConnectionRepository.delete(connection);
    }
}
