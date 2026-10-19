package com.wattpilot.admin.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Statistics for one Europe/Oslo date range.
 *
 * @param from    first day of the range, or {@code null} when the range covers all time
 * @param to      last day of the range, inclusive
 * @param summary totals for the whole range
 * @param daily   one point per day, zero-filled; capped to the most recent 366 days of the range
 * @param sources visitors per traffic source, largest first; the list is capped, so the shares may not add up to 1
 * @param countries visitors per country, largest first; capped like {@code sources}
 */
public record AnalyticsResponse(
        LocalDate from,
        LocalDate to,
        Summary summary,
        List<DailyPoint> daily,
        List<SourceShare> sources,
        List<CountryShare> countries
) {

    /**
     * @param visitorDays                distinct visitors summed over the days of the range; a person who
     *                                   visits on three days counts three times
     * @param newSignups                 non-demo accounts created in the range
     * @param totalSignups               non-demo accounts created up to the end of the range
     * @param signupsPer100VisitorDays   {@code newSignups} per 100 visitor-days, {@code null} without visitors
     * @param schedulesCreated           charging schedules created in the range, cancelled ones included
     * @param usersWithSchedule          non-demo accounts that have ever created a schedule
     * @param totalUsers                 all non-demo accounts today
     * @param activationRate             {@code usersWithSchedule / totalUsers}, {@code null} without users
     */
    public record Summary(
            long visitorDays,
            long newSignups,
            long totalSignups,
            Double signupsPer100VisitorDays,
            long schedulesCreated,
            long usersWithSchedule,
            long totalUsers,
            Double activationRate
    ) {
    }

    public record DailyPoint(LocalDate date, long visitors, long signups, long schedules) {
    }

    /** {@code source} is {@code null} for visits recorded before sources were tracked. */
    public record SourceShare(String source, long visitors, double share) {
    }

    /** {@code countryCode} is {@code null} when the country is unknown. */
    public record CountryShare(String countryCode, String name, long visitors, double share) {
    }
}
