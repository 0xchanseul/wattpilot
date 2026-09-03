package com.wattpilot.charging.entity;

import com.wattpilot.charging.dto.EvSnapshot;
import com.wattpilot.charging.dto.OptimizationResult;
import com.wattpilot.common.PriceArea;
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
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * One charging-plan optimization attempt and its result.
 *
 * <p>The request inputs and an EV figure snapshot are always stored, so a plan stays reproducible
 * even after the EV is edited or the hourly prices are re-imported. A {@link ChargingPlanStatus#SUCCEEDED}
 * plan additionally carries the full recommendation and its {@link ChargingPlanSlot slots}; a
 * {@link ChargingPlanStatus#FAILED} plan carries only {@code failureReason}.
 */
@Entity
@Table(name = "charging_plans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChargingPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Owner and EV are referenced by id, not by a JPA association, so the charging module does not
    // depend on the user or ev entities. Referential integrity is enforced by the FK constraints.
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "ev_id", nullable = false, updatable = false)
    private Long evId;

    @Column(name = "current_battery_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal currentBatteryPercent;

    @Column(name = "target_battery_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal targetBatteryPercent;

    // Stored as VARCHAR(20) with a CHECK constraint, not a database enum type.
    @Enumerated(EnumType.STRING)
    @Column(name = "price_area", nullable = false, length = 20)
    private PriceArea priceArea;

    /** Effective window start the optimizer used: the requested value clamped to now, minute-aligned. */
    @Column(name = "earliest_start_at", nullable = false)
    private OffsetDateTime earliestStartAt;

    @Column(name = "required_completion_at", nullable = false)
    private OffsetDateTime requiredCompletionAt;

    // EV figure snapshot the optimizer worked from.
    @Column(name = "ev_name", nullable = false, length = 100)
    private String evName;

    @Column(name = "ev_manufacturer", nullable = false, length = 100)
    private String evManufacturer;

    @Column(name = "ev_model", nullable = false, length = 100)
    private String evModel;

    @Column(name = "battery_capacity_kwh", nullable = false, precision = 8, scale = 2)
    private BigDecimal batteryCapacityKwh;

    @Column(name = "max_ac_charging_power_kw", nullable = false, precision = 8, scale = 2)
    private BigDecimal maxAcChargingPowerKw;

    @Column(name = "default_charger_power_kw", nullable = false, precision = 8, scale = 2)
    private BigDecimal defaultChargerPowerKw;

    /** Battery-side energy to add. Null on a FAILED plan. */
    @Column(name = "calculated_energy_kwh", precision = 8, scale = 2)
    private BigDecimal calculatedEnergyKwh;

    /** {@code min(maxAcChargingPowerKw, defaultChargerPowerKw)}, efficiency not applied. Null on a FAILED plan. */
    @Column(name = "effective_charging_power_kw", precision = 8, scale = 2)
    private BigDecimal effectiveChargingPowerKw;

    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    @Column(name = "recommended_start_at")
    private OffsetDateTime recommendedStartAt;

    @Column(name = "recommended_end_at")
    private OffsetDateTime recommendedEndAt;

    /** Grid-side energy drawn over the window ({@code calculatedEnergyKwh / efficiency}). Null on a FAILED plan. */
    @Column(name = "expected_energy_kwh", precision = 8, scale = 2)
    private BigDecimal expectedEnergyKwh;

    @Column(name = "estimated_cost_nok", precision = 12, scale = 4)
    private BigDecimal estimatedCostNok;

    @Column(name = "baseline_cost_nok", precision = 12, scale = 4)
    private BigDecimal baselineCostNok;

    @Column(name = "expected_savings_nok", precision = 12, scale = 4)
    private BigDecimal expectedSavingsNok;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ChargingPlanStatus status;

    /** Human-readable reason the optimization was infeasible. Null on a SUCCEEDED plan. */
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    private ChargingPlan(Long userId, Long evId, PriceArea priceArea, BigDecimal currentBatteryPercent,
                         BigDecimal targetBatteryPercent, OffsetDateTime earliestStartAt,
                         OffsetDateTime requiredCompletionAt, EvSnapshot evSnapshot) {
        this.userId = userId;
        this.evId = evId;
        this.priceArea = priceArea;
        this.currentBatteryPercent = currentBatteryPercent;
        this.targetBatteryPercent = targetBatteryPercent;
        this.earliestStartAt = earliestStartAt;
        this.requiredCompletionAt = requiredCompletionAt;
        this.evName = evSnapshot.name();
        this.evManufacturer = evSnapshot.manufacturer();
        this.evModel = evSnapshot.model();
        this.batteryCapacityKwh = evSnapshot.batteryCapacityKwh();
        this.maxAcChargingPowerKw = evSnapshot.maxAcChargingPowerKw();
        this.defaultChargerPowerKw = evSnapshot.defaultChargerPowerKw();
    }

    public static ChargingPlan succeeded(Long userId, Long evId, PriceArea priceArea,
                                         BigDecimal currentBatteryPercent, BigDecimal targetBatteryPercent,
                                         OffsetDateTime earliestStartAt, OffsetDateTime requiredCompletionAt,
                                         OptimizationResult.Success result) {
        ChargingPlan plan = new ChargingPlan(userId, evId, priceArea, currentBatteryPercent, targetBatteryPercent,
                earliestStartAt, requiredCompletionAt, result.evSnapshot());
        plan.status = ChargingPlanStatus.SUCCEEDED;
        plan.calculatedEnergyKwh = result.calculatedEnergyKwh();
        plan.effectiveChargingPowerKw = result.effectiveChargingPowerKw();
        plan.estimatedDurationMinutes = result.estimatedDurationMinutes();
        plan.recommendedStartAt = result.recommendedStartAt();
        plan.recommendedEndAt = result.recommendedEndAt();
        plan.expectedEnergyKwh = result.expectedEnergyKwh();
        plan.estimatedCostNok = result.estimatedCostNok();
        plan.baselineCostNok = result.baselineCostNok();
        plan.expectedSavingsNok = result.expectedSavingsNok();
        return plan;
    }

    public static ChargingPlan failed(Long userId, Long evId, PriceArea priceArea,
                                      BigDecimal currentBatteryPercent, BigDecimal targetBatteryPercent,
                                      OffsetDateTime earliestStartAt, OffsetDateTime requiredCompletionAt,
                                      EvSnapshot evSnapshot, String failureReason) {
        ChargingPlan plan = new ChargingPlan(userId, evId, priceArea, currentBatteryPercent, targetBatteryPercent,
                earliestStartAt, requiredCompletionAt, evSnapshot);
        plan.status = ChargingPlanStatus.FAILED;
        plan.failureReason = failureReason;
        return plan;
    }
}
