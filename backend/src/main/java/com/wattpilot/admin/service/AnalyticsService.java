package com.wattpilot.admin.service;

import com.wattpilot.admin.dto.AnalyticsResponse;
import com.wattpilot.admin.dto.AnalyticsResponse.CountryShare;
import com.wattpilot.admin.dto.AnalyticsResponse.DailyPoint;
import com.wattpilot.admin.dto.AnalyticsResponse.SourceShare;
import com.wattpilot.admin.dto.AnalyticsResponse.Summary;
import com.wattpilot.admin.repository.AnalyticsRepository;
import com.wattpilot.auth.DemoProperties;
import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/**
 * Builds the operator dashboard figures. Everything is computed from the tables at request time:
 * with one reader and a small data set that is cheaper to keep correct than a pre-aggregated copy.
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Oslo");

    // Stands in for "no lower bound" when the range covers all time.
    private static final LocalDate EPOCH = LocalDate.of(2000, 1, 1);
    private static final OffsetDateTime FAR_FUTURE = OffsetDateTime.parse("2100-01-01T00:00:00Z");

    // Longest daily series returned, so an all-time request cannot produce an unbounded chart.
    private static final int MAX_SERIES_DAYS = 366;
    private static final int MAX_BREAKDOWN_ROWS = 20;

    private final AnalyticsRepository repository;
    private final DemoProperties demoProperties;
    private final Clock clock;

    public AnalyticsService(AnalyticsRepository repository, DemoProperties demoProperties, Clock clock) {
        this.repository = repository;
        this.demoProperties = demoProperties;
        this.clock = clock;
    }

    /**
     * @param from first day, inclusive; {@code null} for all time
     * @param to   last day, inclusive; {@code null} for today
     */
    public AnalyticsResponse analytics(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        LocalDate end = to != null ? to : today;
        if (from != null && from.isAfter(end)) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE, "The 'to' date must not be before 'from'.");
        }
        LocalDate start = from != null ? from : EPOCH;
        OffsetDateTime startTs = start.atStartOfDay(ZONE).toOffsetDateTime();
        OffsetDateTime endTs = end.plusDays(1).atStartOfDay(ZONE).toOffsetDateTime();
        String templateEmail = demoProperties.templateEmail() == null ? "" : demoProperties.templateEmail().trim();

        long visitorDays = repository.countVisitorDays(start, end);
        long newSignups = repository.countSignups(startTs, endTs, templateEmail);
        long totalSignups = repository.countSignups(EPOCH.atStartOfDay(ZONE).toOffsetDateTime(), endTs, templateEmail);
        long totalUsers = repository.countSignups(EPOCH.atStartOfDay(ZONE).toOffsetDateTime(), FAR_FUTURE, templateEmail);
        long usersWithSchedule = repository.countUsersWithSchedule(templateEmail);

        Summary summary = new Summary(
                visitorDays,
                newSignups,
                totalSignups,
                visitorDays == 0 ? null : round(newSignups * 100.0 / visitorDays, 2),
                repository.countSchedules(startTs, endTs, templateEmail),
                usersWithSchedule,
                totalUsers,
                totalUsers == 0 ? null : round((double) usersWithSchedule / totalUsers, 4));

        return new AnalyticsResponse(
                from,
                end,
                summary,
                dailyPoints(from, end, templateEmail),
                repository.visitorsBySource(start, end, MAX_BREAKDOWN_ROWS).stream()
                        .map(row -> new SourceShare(row.label(), row.count(), share(row.count(), visitorDays)))
                        .toList(),
                repository.visitorsByCountry(start, end, MAX_BREAKDOWN_ROWS).stream()
                        .map(row -> new CountryShare(
                                row.label(), countryName(row.label()), row.count(), share(row.count(), visitorDays)))
                        .toList());
    }

    private List<DailyPoint> dailyPoints(LocalDate from, LocalDate end, String templateEmail) {
        LocalDate seriesStart = from != null ? from : repository.firstVisitDate().orElse(end);
        LocalDate earliestAllowed = end.minusDays(MAX_SERIES_DAYS - 1L);
        if (seriesStart.isBefore(earliestAllowed)) {
            seriesStart = earliestAllowed;
        }
        if (seriesStart.isAfter(end)) {
            seriesStart = end;
        }
        return repository.dailyCounts(seriesStart, end, templateEmail).stream()
                .map(row -> new DailyPoint(row.date(), row.visitors(), row.signups(), row.schedules()))
                .toList();
    }

    private static double share(long count, long total) {
        return total == 0 ? 0 : round((double) count / total, 4);
    }

    private static double round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    private static String countryName(String code) {
        if (code == null) {
            return "Unknown";
        }
        String name = Locale.of("", code).getDisplayCountry(Locale.ENGLISH);
        return name.isBlank() ? code : name;
    }
}
