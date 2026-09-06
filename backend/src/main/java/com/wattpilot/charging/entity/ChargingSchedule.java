package com.wattpilot.charging.entity;

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
 * An execution reservation for one confirmed charging plan.
 *
 * <p>Created together with its {@link ChargingPlan} in a single transaction when the user confirms a
 * previewed candidate. The owning user and EV are reached through {@code chargingPlanId}, so this
 * table never trusts a client-supplied owner. {@code scheduledStartAt}/{@code scheduledEndAt} mirror
 * the plan's recommended window and drive the overlap check.
 */
@Entity
@Table(name = "charging_schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChargingSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "charging_plan_id", nullable = false, updatable = false)
    private Long chargingPlanId;

    @Column(name = "scheduled_start_at", nullable = false)
    private OffsetDateTime scheduledStartAt;

    @Column(name = "scheduled_end_at", nullable = false)
    private OffsetDateTime scheduledEndAt;

    @Column(name = "expected_energy_kwh", nullable = false, precision = 8, scale = 2)
    private BigDecimal expectedEnergyKwh;

    @Column(name = "estimated_cost_nok", nullable = false, precision = 12, scale = 4)
    private BigDecimal estimatedCostNok;

    // Maps onto the PostgreSQL charging_schedule_status enum type declared in V1__init_schema.sql.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "charging_schedule_status")
    private ChargingScheduleStatus status;

    /**
     * Consecutive transient execution errors since the last successful phase transition. Reset to 0
     * whenever the schedule moves to {@link ChargingScheduleStatus#IN_PROGRESS} (a fresh budget for the
     * completion attempt) and left at its final value on a terminal status, for audit purposes.
     */
    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    /** Earliest time the scheduler may retry after a transient error. Null outside a backoff wait. */
    @Column(name = "next_retry_at")
    private OffsetDateTime nextRetryAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    private ChargingSchedule(Long chargingPlanId, OffsetDateTime scheduledStartAt, OffsetDateTime scheduledEndAt,
                             BigDecimal expectedEnergyKwh, BigDecimal estimatedCostNok) {
        this.chargingPlanId = chargingPlanId;
        this.scheduledStartAt = scheduledStartAt;
        this.scheduledEndAt = scheduledEndAt;
        this.expectedEnergyKwh = expectedEnergyKwh;
        this.estimatedCostNok = estimatedCostNok;
        this.status = ChargingScheduleStatus.WAITING;
    }

    public static ChargingSchedule create(Long chargingPlanId, OffsetDateTime scheduledStartAt,
                                          OffsetDateTime scheduledEndAt, BigDecimal expectedEnergyKwh,
                                          BigDecimal estimatedCostNok) {
        return new ChargingSchedule(chargingPlanId, scheduledStartAt, scheduledEndAt, expectedEnergyKwh,
                estimatedCostNok);
    }

    /** The start or completion attempt succeeded; clears any retry bookkeeping from prior attempts. */
    public void markInProgress() {
        this.status = ChargingScheduleStatus.IN_PROGRESS;
        clearRetryState();
    }

    public void markCompleted() {
        this.status = ChargingScheduleStatus.COMPLETED;
        clearRetryState();
    }

    /** A definitive business or system failure, or a missed execution window. Terminal. */
    public void markFailed() {
        this.status = ChargingScheduleStatus.FAILED;
        clearRetryState();
    }

    /** Cancelled by the user while still {@link ChargingScheduleStatus#WAITING}. Terminal. */
    public void markCancelled() {
        this.status = ChargingScheduleStatus.CANCELLED;
        clearRetryState();
    }

    /** A transient execution error, with attempts remaining: record it and back off before the next try. */
    public void scheduleRetry(int retryCount, OffsetDateTime nextRetryAt) {
        this.retryCount = retryCount;
        this.nextRetryAt = nextRetryAt;
    }

    private void clearRetryState() {
        this.nextRetryAt = null;
    }
}
