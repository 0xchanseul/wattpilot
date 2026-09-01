package com.wattpilot.ev.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * A manually registered electric vehicle owned by a user.
 *
 * <p>The battery and charging-power figures are entered by hand in V1 and are the inputs the
 * charging optimizer works from. The optimizer snapshots them onto each charging plan, so editing
 * an EV later never rewrites past plans.
 */
@Entity
@Table(name = "evs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ev {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The owning user is referenced by id, not by a JPA association, so the ev module does not
    // depend on the user entity. Referential integrity is enforced by the fk_evs_user constraint.
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "manufacturer", nullable = false, length = 100)
    private String manufacturer;

    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @Column(name = "battery_capacity_kwh", nullable = false, precision = 8, scale = 2)
    private BigDecimal batteryCapacityKwh;

    @Column(name = "max_ac_charging_power_kw", nullable = false, precision = 8, scale = 2)
    private BigDecimal maxAcChargingPowerKw;

    @Column(name = "default_charger_power_kw", nullable = false, precision = 8, scale = 2)
    private BigDecimal defaultChargerPowerKw;

    // Maps onto the PostgreSQL ev_status enum type declared in V1__init_schema.sql.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "ev_status")
    private EvStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    private Ev(Long userId, String name, String manufacturer, String model, BigDecimal batteryCapacityKwh,
               BigDecimal maxAcChargingPowerKw, BigDecimal defaultChargerPowerKw) {
        this.userId = userId;
        this.name = name;
        this.manufacturer = manufacturer;
        this.model = model;
        this.batteryCapacityKwh = batteryCapacityKwh;
        this.maxAcChargingPowerKw = maxAcChargingPowerKw;
        this.defaultChargerPowerKw = defaultChargerPowerKw;
        this.status = EvStatus.ACTIVE;
    }

    public static Ev register(Long userId, String name, String manufacturer, String model,
                              BigDecimal batteryCapacityKwh, BigDecimal maxAcChargingPowerKw,
                              BigDecimal defaultChargerPowerKw) {
        return new Ev(userId, name, manufacturer, model, batteryCapacityKwh, maxAcChargingPowerKw,
                defaultChargerPowerKw);
    }

    /**
     * Applies a partial update: only non-null arguments overwrite the current value, so an absent
     * field in a PATCH request leaves the stored value untouched.
     */
    public void updateProfile(String name, String manufacturer, String model, BigDecimal batteryCapacityKwh,
                              BigDecimal maxAcChargingPowerKw, BigDecimal defaultChargerPowerKw) {
        if (name != null) {
            this.name = name;
        }
        if (manufacturer != null) {
            this.manufacturer = manufacturer;
        }
        if (model != null) {
            this.model = model;
        }
        if (batteryCapacityKwh != null) {
            this.batteryCapacityKwh = batteryCapacityKwh;
        }
        if (maxAcChargingPowerKw != null) {
            this.maxAcChargingPowerKw = maxAcChargingPowerKw;
        }
        if (defaultChargerPowerKw != null) {
            this.defaultChargerPowerKw = defaultChargerPowerKw;
        }
    }

    public void changeStatus(EvStatus status) {
        if (status != null) {
            this.status = status;
        }
    }

    public void deactivate() {
        this.status = EvStatus.INACTIVE;
    }

    public boolean isActive() {
        return status == EvStatus.ACTIVE;
    }
}
