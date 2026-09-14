package com.wattpilot.dashboard.dto;

import java.util.List;

/**
 * The aggregate payload for {@code GET /dashboard}: everything the home screen needs in one call.
 * {@code nextCharging} and {@code currentPrice} are independently nullable (no active reservation, or
 * no price covers the current hour); every other block is always present, empty when there is no data.
 */
public record DashboardResponse(
        DashboardSummary summary,
        NextCharging nextCharging,
        CurrentElectricityPrice currentPrice,
        List<SavingsTrendPoint> savingsTrend,
        CostComparison costComparison,
        List<RecentChargingSession> recentSessions
) {
}
