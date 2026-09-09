package com.wattpilot.history.dto;

import com.wattpilot.charging.entity.ChargingFailureCode;
import com.wattpilot.charging.entity.ChargingSessionStatus;
import com.wattpilot.electricity.service.ElectricityPriceService;
import com.wattpilot.history.repository.ChargingHistoryRow;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * One executed charging session in the caller's history. Matches the {@code ChargingHistoryItem}
 * schema in docs/openapi.yaml.
 *
 * <p>{@code evName} is the snapshot stored on the charging plan at confirmation time, so it stays
 * stable after a later EV rename or deactivation.
 *
 * <p><b>Planned vs. realized.</b> Every cost/energy figure below is non-null only for a
 * {@link ChargingSessionStatus#COMPLETED} session ({@link ChargingSessionStatus#FAILED} carries
 * {@code failureCode}/{@code failureReason} instead):
 * <ul>
 *   <li>{@code optimizedCostNok} — the optimizer's expected cost for the chosen window (plan snapshot).</li>
 *   <li>{@code estimatedSavingsNok} — {@code baselineCostNok - optimizedCostNok}: the saving the plan predicted.</li>
 *   <li>{@code actualEnergyKwh} / {@code actualCostNok} — the realized outcome recorded when the session completed.</li>
 *   <li>{@code realizedSavingsNok} — {@code baselineCostNok - actualCostNok}: the saving actually delivered.</li>
 * </ul>
 * In V1 mock charging finishes exactly as planned, so {@code optimizedCostNok == actualCostNok} and
 * the two savings figures are equal; they are kept distinct because that will not hold once real
 * charging can under- or over-deliver. Timestamps are rendered in Europe/Oslo.
 */
public record ChargingHistoryItem(
        Long sessionId,
        Long scheduleId,
        Long evId,
        String evName,
        ChargingSessionStatus status,
        /** When the outcome was recorded (the list's sort key); present on every entry. */
        OffsetDateTime recordedAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        BigDecimal baselineCostNok,
        BigDecimal optimizedCostNok,
        BigDecimal estimatedSavingsNok,
        BigDecimal actualEnergyKwh,
        BigDecimal actualCostNok,
        BigDecimal realizedSavingsNok,
        ChargingFailureCode failureCode,
        String failureReason
) {

    private static final ZoneId DISPLAY_ZONE = ElectricityPriceService.PRICE_ZONE;

    public static ChargingHistoryItem from(ChargingHistoryRow row) {
        return new ChargingHistoryItem(
                row.sessionId(),
                row.scheduleId(),
                row.evId(),
                row.evName(),
                row.status(),
                atDisplayZone(row.recordedAt()),
                atDisplayZone(row.startedAt()),
                atDisplayZone(row.completedAt()),
                row.baselineCostNok(),
                row.optimizedCostNok(),
                difference(row.baselineCostNok(), row.optimizedCostNok()),
                row.actualEnergyKwh(),
                row.actualCostNok(),
                difference(row.baselineCostNok(), row.actualCostNok()),
                row.failureCode(),
                row.failureReason());
    }

    /** {@code a - b}, or null when either side is absent (a FAILED session has no cost figures). */
    static BigDecimal difference(BigDecimal a, BigDecimal b) {
        return a == null || b == null ? null : a.subtract(b);
    }

    private static OffsetDateTime atDisplayZone(OffsetDateTime value) {
        return value == null ? null : value.atZoneSameInstant(DISPLAY_ZONE).toOffsetDateTime();
    }
}
