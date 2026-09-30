package com.wattpilot.ev.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/**
 * Read-only link between a WattPilot {@link Ev} and a Smartcar vehicle.
 *
 * <p>Smartcar API v3 uses one application-level access token (no per-vehicle OAuth tokens), so no
 * secret lives here: only the ids needed to scope a Smartcar request to this vehicle/user. Telemetry
 * is fetched live from Smartcar on every read and is never persisted.
 */
@Entity
@Table(name = "vehicle_connections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VehicleConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "ev_id", nullable = false, updatable = false)
    private Long evId;

    @Column(name = "smartcar_user_id", nullable = false, updatable = false, length = 64)
    private String smartcarUserId;

    @Column(name = "smartcar_vehicle_id", nullable = false, updatable = false, length = 64)
    private String smartcarVehicleId;

    @Column(name = "smartcar_connection_id", nullable = false, updatable = false, length = 64)
    private String smartcarConnectionId;

    @Column(name = "vehicle_make", length = 100)
    private String vehicleMake;

    @Column(name = "vehicle_model", length = 100)
    private String vehicleModel;

    @Column(name = "vehicle_year")
    private Integer vehicleYear;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    private VehicleConnection(Long userId, Long evId, String smartcarUserId, String smartcarVehicleId,
                              String smartcarConnectionId, String vehicleMake, String vehicleModel,
                              Integer vehicleYear) {
        this.userId = userId;
        this.evId = evId;
        this.smartcarUserId = smartcarUserId;
        this.smartcarVehicleId = smartcarVehicleId;
        this.smartcarConnectionId = smartcarConnectionId;
        this.vehicleMake = vehicleMake;
        this.vehicleModel = vehicleModel;
        this.vehicleYear = vehicleYear;
    }

    public static VehicleConnection link(Long userId, Long evId, String smartcarUserId, String smartcarVehicleId,
                                         String smartcarConnectionId, String vehicleMake, String vehicleModel,
                                         Integer vehicleYear) {
        return new VehicleConnection(userId, evId, smartcarUserId, smartcarVehicleId, smartcarConnectionId,
                vehicleMake, vehicleModel, vehicleYear);
    }
}
