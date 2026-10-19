package com.wattpilot.admin.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Read-only aggregate queries over the visitor, user and charging tables.
 *
 * <p>Plain SQL rather than entities: these are reporting queries across modules, and going through
 * other modules' entities would couple the statistics to their persistence model. Accounts counted as
 * real users exclude demo visitor accounts and the demo template account, whose copied history keeps
 * its original dates.
 */
@Repository
public class AnalyticsRepository {

    private static final String ZONE = "Europe/Oslo";

    // A blank template email matches no account, so the comparison needs no special case.
    private static final String REAL_USER = "u.demo = FALSE AND lower(u.email) <> lower(:templateEmail)";

    private static final String SCHEDULE_OWNER = """
            FROM charging_schedules cs
            JOIN charging_plans cp ON cp.id = cs.charging_plan_id
            JOIN users u ON u.id = cp.user_id
            """;

    public record LabelCount(String label, long count) {
    }

    public record DailyCounts(LocalDate date, long visitors, long signups, long schedules) {
    }

    private final JdbcClient jdbcClient;

    public AnalyticsRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public long countVisitorDays(LocalDate from, LocalDate to) {
        return jdbcClient.sql("SELECT count(*) FROM daily_visitors WHERE visit_date BETWEEN :from AND :to")
                .param("from", from)
                .param("to", to)
                .query(Long.class)
                .single();
    }

    /** Visitors per source; visits without a recorded source come back with a {@code null} label. */
    public List<LabelCount> visitorsBySource(LocalDate from, LocalDate to, int limit) {
        return groupedVisitors("source", from, to, limit);
    }

    public List<LabelCount> visitorsByCountry(LocalDate from, LocalDate to, int limit) {
        return groupedVisitors("country_code", from, to, limit);
    }

    // The column name is one of two constants chosen by this class, never caller input.
    private List<LabelCount> groupedVisitors(String column, LocalDate from, LocalDate to, int limit) {
        return jdbcClient.sql("SELECT " + column + " AS label, count(*) AS total FROM daily_visitors "
                        + "WHERE visit_date BETWEEN :from AND :to GROUP BY " + column
                        + " ORDER BY total DESC, label NULLS LAST LIMIT :limit")
                .param("from", from)
                .param("to", to)
                .param("limit", limit)
                .query((rs, rowNum) -> new LabelCount(rs.getString("label"), rs.getLong("total")))
                .list();
    }

    public long countSignups(OffsetDateTime fromInclusive, OffsetDateTime toExclusive, String templateEmail) {
        return jdbcClient.sql("SELECT count(*) FROM users u WHERE " + REAL_USER
                        + " AND u.created_at >= :from AND u.created_at < :to")
                .param("templateEmail", templateEmail)
                .param("from", fromInclusive)
                .param("to", toExclusive)
                .query(Long.class)
                .single();
    }

    public long countSchedules(OffsetDateTime fromInclusive, OffsetDateTime toExclusive, String templateEmail) {
        return jdbcClient.sql("SELECT count(*) " + SCHEDULE_OWNER + "WHERE " + REAL_USER
                        + " AND cs.created_at >= :from AND cs.created_at < :to")
                .param("templateEmail", templateEmail)
                .param("from", fromInclusive)
                .param("to", toExclusive)
                .query(Long.class)
                .single();
    }

    public long countUsersWithSchedule(String templateEmail) {
        return jdbcClient.sql("SELECT count(DISTINCT u.id) " + SCHEDULE_OWNER + "WHERE " + REAL_USER)
                .param("templateEmail", templateEmail)
                .query(Long.class)
                .single();
    }

    /** One row per day from {@code from} to {@code to}, zero where nothing happened. */
    public List<DailyCounts> dailyCounts(LocalDate from, LocalDate to, String templateEmail) {
        String sql = """
                WITH days AS (
                    SELECT CAST(d AS date) AS day
                    FROM generate_series(CAST(:from AS date), CAST(:to AS date), INTERVAL '1 day') AS d
                ),
                visitors AS (
                    SELECT visit_date AS day, count(*) AS total
                    FROM daily_visitors
                    WHERE visit_date BETWEEN :from AND :to
                    GROUP BY visit_date
                ),
                signups AS (
                    SELECT CAST(u.created_at AT TIME ZONE '%1$s' AS date) AS day, count(*) AS total
                    FROM users u
                    WHERE %2$s AND u.created_at >= :fromTs AND u.created_at < :toTs
                    GROUP BY 1
                ),
                schedules AS (
                    SELECT CAST(cs.created_at AT TIME ZONE '%1$s' AS date) AS day, count(*) AS total
                    %3$s
                    WHERE %2$s AND cs.created_at >= :fromTs AND cs.created_at < :toTs
                    GROUP BY 1
                )
                SELECT days.day,
                       COALESCE(visitors.total, 0) AS visitors,
                       COALESCE(signups.total, 0) AS signups,
                       COALESCE(schedules.total, 0) AS schedules
                FROM days
                LEFT JOIN visitors ON visitors.day = days.day
                LEFT JOIN signups ON signups.day = days.day
                LEFT JOIN schedules ON schedules.day = days.day
                ORDER BY days.day
                """.formatted(ZONE, REAL_USER, SCHEDULE_OWNER);
        return jdbcClient.sql(sql)
                .param("from", from)
                .param("to", to)
                .param("fromTs", from.atStartOfDay(java.time.ZoneId.of(ZONE)).toOffsetDateTime())
                .param("toTs", to.plusDays(1).atStartOfDay(java.time.ZoneId.of(ZONE)).toOffsetDateTime())
                .param("templateEmail", templateEmail)
                .query((rs, rowNum) -> new DailyCounts(
                        rs.getObject("day", LocalDate.class),
                        rs.getLong("visitors"),
                        rs.getLong("signups"),
                        rs.getLong("schedules")))
                .list();
    }

    /** Earliest day with a recorded visit, used as the start of an all-time chart. */
    public Optional<LocalDate> firstVisitDate() {
        return jdbcClient.sql("SELECT min(visit_date) FROM daily_visitors")
                .query((rs, rowNum) -> rs.getObject(1, LocalDate.class))
                .optional();
    }
}
