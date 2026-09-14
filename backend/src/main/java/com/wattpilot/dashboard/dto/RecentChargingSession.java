package com.wattpilot.dashboard.dto;

import com.wattpilot.dashboard.repository.RecentSessionRow;
import com.wattpilot.electricity.service.ElectricityPriceService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * One recently completed charging session, slimmed down from
 * {@link com.wattpilot.history.dto.ChargingHistoryItem} for the Dashboard's home-screen list. Named
 * {@code realizedSavingsNok} (not {@code estimatedSavingsNok}) since the charge already happened and
 * the actual outcome is known — matches {@code ChargingHistoryItem}'s convention.
 */
public record RecentChargingSession(
        Long sessionId,
        Long evId,
        String evName,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        BigDecimal actualEnergyKwh,
        BigDecimal actualCostNok,
        BigDecimal realizedSavingsNok
) {

    private static final ZoneId DISPLAY_ZONE = ElectricityPriceService.PRICE_ZONE;

    public static RecentChargingSession from(RecentSessionRow row) {
        return new RecentChargingSession(
                row.sessionId(),
                row.evId(),
                row.evName(),
                atDisplayZone(row.startedAt()),
                atDisplayZone(row.completedAt()),
                row.actualEnergyKwh(),
                row.actualCostNok(),
                row.baselineCostNok().subtract(row.actualCostNok()));
    }

    private static OffsetDateTime atDisplayZone(OffsetDateTime value) {
        return value == null ? null : value.atZoneSameInstant(DISPLAY_ZONE).toOffsetDateTime();
    }
}
