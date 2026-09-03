package com.wattpilot.charging.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * One price slot of a {@link ChargingPlanStatus#SUCCEEDED} plan's continuous charging window,
 * persisted in calculation order ({@code sequenceNo} starting at 1).
 *
 * <p>The first and last slot of a window may be partial: {@code slotStartAt}/{@code slotEndAt} are
 * the real overlap with the charging window, and {@code plannedEnergyKwh}/{@code expectedCostNok}
 * are prorated to it. {@code pricePerKwh} is a snapshot of the hour's price at calculation time,
 * because {@code electricity_prices} rows are upserted in place on re-import.
 */
@Entity
@Table(name = "charging_plan_slots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChargingPlanSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "charging_plan_id", nullable = false, updatable = false)
    private Long chargingPlanId;

    @Column(name = "electricity_price_id", nullable = false, updatable = false)
    private Long electricityPriceId;

    @Column(name = "slot_start_at", nullable = false)
    private OffsetDateTime slotStartAt;

    @Column(name = "slot_end_at", nullable = false)
    private OffsetDateTime slotEndAt;

    @Column(name = "price_per_kwh", nullable = false, precision = 12, scale = 6)
    private BigDecimal pricePerKwh;

    /** Grid-side energy drawn in this slot. */
    @Column(name = "planned_energy_kwh", nullable = false, precision = 8, scale = 2)
    private BigDecimal plannedEnergyKwh;

    @Column(name = "expected_cost_nok", nullable = false, precision = 12, scale = 4)
    private BigDecimal expectedCostNok;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    private ChargingPlanSlot(Long chargingPlanId, Long electricityPriceId, OffsetDateTime slotStartAt,
                             OffsetDateTime slotEndAt, BigDecimal pricePerKwh, BigDecimal plannedEnergyKwh,
                             BigDecimal expectedCostNok, int sequenceNo) {
        this.chargingPlanId = chargingPlanId;
        this.electricityPriceId = electricityPriceId;
        this.slotStartAt = slotStartAt;
        this.slotEndAt = slotEndAt;
        this.pricePerKwh = pricePerKwh;
        this.plannedEnergyKwh = plannedEnergyKwh;
        this.expectedCostNok = expectedCostNok;
        this.sequenceNo = sequenceNo;
    }

    public static ChargingPlanSlot of(Long chargingPlanId, Long electricityPriceId, OffsetDateTime slotStartAt,
                                      OffsetDateTime slotEndAt, BigDecimal pricePerKwh, BigDecimal plannedEnergyKwh,
                                      BigDecimal expectedCostNok, int sequenceNo) {
        return new ChargingPlanSlot(chargingPlanId, electricityPriceId, slotStartAt, slotEndAt, pricePerKwh,
                plannedEnergyKwh, expectedCostNok, sequenceNo);
    }
}
