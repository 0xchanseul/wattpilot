package com.wattpilot.charging.dto;

import java.util.List;

/**
 * The Schedules screen payload: everything that is current or near-future, plus a short tail of what
 * just happened. It is deliberately <b>not</b> a paginated list.
 *
 * <ul>
 *   <li>{@code upcoming} — {@code WAITING} reservations, earliest start first.</li>
 *   <li>{@code inProgress} — reservations currently being executed by Mock Charging.</li>
 *   <li>{@code recentActivity} — the last few {@code COMPLETED}/{@code FAILED} results, newest first,
 *       capped at {@link com.wattpilot.charging.service.ChargingScheduleService#RECENT_ACTIVITY_LIMIT}.
 *       The cap is a role boundary, not a performance tweak: browsing the full history of past
 *       charges is the charging-history endpoints' job, and letting completed rows accumulate here
 *       would make the two features indistinguishable.</li>
 * </ul>
 */
public record ChargingSchedulesOverviewResponse(
        List<ChargingScheduleResponse> upcoming,
        List<ChargingScheduleResponse> inProgress,
        List<ChargingScheduleRecentActivity> recentActivity
) {

    public static ChargingSchedulesOverviewResponse empty() {
        return new ChargingSchedulesOverviewResponse(List.of(), List.of(), List.of());
    }
}
