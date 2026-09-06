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
 * The single execution attempt outcome for one {@link ChargingSchedule}.
 *
 * <p>Created exactly once, at the moment a definitive outcome is known for the schedule's start
 * attempt: {@link #started} on success, {@link #failed} on a business or system failure (including a
 * missed execution window, where {@code startedAt} stays {@code null} because charging never began).
 * While a transient technical error is being retried, no row exists yet — see
 * {@code ChargingExecutionService}. Once created, a session only ever transitions once more, via
 * {@link #complete} or {@link #fail}, when the schedule's completion attempt resolves.
 *
 * <p>{@code baselineCostNok}, {@code optimizedCostNok} and {@code estimatedSavingsNok} are populated
 * only on {@link ChargingSessionStatus#COMPLETED}: they describe a realized outcome, so a failed or
 * cancelled attempt leaves them {@code null} rather than reporting savings that were never delivered.
 */
@Entity
@Table(name = "charging_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChargingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "charging_schedule_id", nullable = false, updatable = false)
    private Long chargingScheduleId;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "actual_energy_kwh", precision = 8, scale = 2)
    private BigDecimal actualEnergyKwh;

    @Column(name = "actual_cost_nok", precision = 12, scale = 4)
    private BigDecimal actualCostNok;

    /** Window-average reference cost, copied from the plan snapshot. Set only on completion. */
    @Column(name = "baseline_cost_nok", precision = 12, scale = 4)
    private BigDecimal baselineCostNok;

    @Column(name = "optimized_cost_nok", precision = 12, scale = 4)
    private BigDecimal optimizedCostNok;

    @Column(name = "estimated_savings_nok", precision = 12, scale = 4)
    private BigDecimal estimatedSavingsNok;

    // Maps onto the PostgreSQL charging_session_status enum type declared in V1__init_schema.sql.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "charging_session_status")
    private ChargingSessionStatus status;

    // Stored as VARCHAR(50) with a CHECK constraint, not a database enum type, matching charging_plans.status.
    @Enumerated(EnumType.STRING)
    @Column(name = "failure_code", length = 50)
    private ChargingFailureCode failureCode;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    private ChargingSession(Long chargingScheduleId, ChargingSessionStatus status, OffsetDateTime startedAt) {
        this.chargingScheduleId = chargingScheduleId;
        this.status = status;
        this.startedAt = startedAt;
    }

    /** The schedule's start attempt succeeded; charging is now in progress. */
    public static ChargingSession started(Long chargingScheduleId, OffsetDateTime startedAt) {
        return new ChargingSession(chargingScheduleId, ChargingSessionStatus.STARTED, startedAt);
    }

    /**
     * The schedule never reached a running charge: either the start attempt returned a definitive
     * business failure, transient errors exhausted their retry budget, or the execution window closed
     * before a start was ever attempted ({@code startedAt} is {@code null} in that last case).
     */
    public static ChargingSession failed(Long chargingScheduleId, OffsetDateTime startedAt,
                                         ChargingFailureCode failureCode, String failureReason) {
        ChargingSession session = new ChargingSession(chargingScheduleId, ChargingSessionStatus.FAILED, startedAt);
        session.failureCode = failureCode;
        session.failureReason = failureReason;
        return session;
    }

    /** The schedule's completion attempt succeeded. Figures come from the plan/slot snapshot, never recomputed. */
    public void complete(OffsetDateTime completedAt, BigDecimal actualEnergyKwh, BigDecimal actualCostNok,
                         BigDecimal baselineCostNok, BigDecimal optimizedCostNok, BigDecimal estimatedSavingsNok) {
        this.status = ChargingSessionStatus.COMPLETED;
        this.completedAt = completedAt;
        this.actualEnergyKwh = actualEnergyKwh;
        this.actualCostNok = actualCostNok;
        this.baselineCostNok = baselineCostNok;
        this.optimizedCostNok = optimizedCostNok;
        this.estimatedSavingsNok = estimatedSavingsNok;
    }

    /** The schedule's completion attempt hit a definitive business or system failure. */
    public void fail(ChargingFailureCode failureCode, String failureReason) {
        this.status = ChargingSessionStatus.FAILED;
        this.failureCode = failureCode;
        this.failureReason = failureReason;
    }
}
