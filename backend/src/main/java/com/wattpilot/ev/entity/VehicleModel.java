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

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * A manually curated vehicle specification preset (V1.5 master data), used only to prefill the EV
 * registration form. Rows are seeded by Flyway; there is no create/update API, so this entity has no
 * mutating methods.
 */
@Entity
@Table(name = "vehicle_models")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VehicleModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "manufacturer", nullable = false, length = 100)
    private String manufacturer;

    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @Column(name = "battery_capacity_kwh", nullable = false, precision = 8, scale = 2)
    private BigDecimal batteryCapacityKwh;

    @Column(name = "max_ac_charging_power_kw", nullable = false, precision = 8, scale = 2)
    private BigDecimal maxAcChargingPowerKw;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
