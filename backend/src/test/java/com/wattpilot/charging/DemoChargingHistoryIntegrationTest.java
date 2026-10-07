package com.wattpilot.charging;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies what a demo visitor receives of the template account's charging history, against a real
 * PostgreSQL instance: only finished charges are copied, they follow the EV they belong to, every value
 * (including the timestamps the history orders by) is kept as is, and the template is left untouched.
 */
@SpringBootTest(properties = {
        "wattpilot.demo.enabled=true",
        "wattpilot.demo.template-email=history-template@example.com"
})
@AutoConfigureMockMvc
@Testcontainers
class DemoChargingHistoryIntegrationTest {

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    private static final String TEMPLATE_EMAIL = "history-template@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long templateUserId;

    @BeforeEach
    void ensureTemplateWithHistory() throws Exception {
        List<Long> existing = jdbcTemplate.queryForList(
                "SELECT id FROM users WHERE email = ?", Long.class, TEMPLATE_EMAIL);
        if (!existing.isEmpty()) {
            templateUserId = existing.get(0);
            return;
        }
        String signUp = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"wattpilot-secret","name":"Template","defaultPriceArea":"NO1"}
                                """.formatted(TEMPLATE_EMAIL)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        templateUserId = ((Number) JsonPath.read(signUp, "$.user.id")).longValue();
        String token = JsonPath.read(signUp, "$.accessToken");
        String evBody = mockMvc.perform(post("/api/v1/evs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Demo i4","manufacturer":"BMW","model":"i4 eDrive40","batteryCapacityKwh":81.1,"maxAcChargingPowerKw":11,"defaultChargerPowerKw":7.4}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long evId = ((Number) JsonPath.read(evBody, "$.id")).longValue();

        Long priceId = jdbcTemplate.queryForObject("""
                INSERT INTO electricity_prices (price_area, starts_at, ends_at, price_per_kwh, fetched_at)
                VALUES ('NO1', now() - interval '10 days', now() - interval '10 days' + interval '1 hour', 0.5, now())
                RETURNING id
                """, Long.class);

        // Newest first: the two finished charges, then one of each state that must not be copied.
        insertCharge(evId, priceId, 1, "COMPLETED", "COMPLETED");
        insertCharge(evId, priceId, 2, "FAILED", "FAILED");
        insertCharge(evId, priceId, 3, "CANCELLED", null);
        insertCharge(evId, priceId, 4, "WAITING", null);
        insertCharge(evId, priceId, 5, "IN_PROGRESS", "STARTED");
    }

    @Test
    void aVisitorReceivesOnlyTheFinishedChargesOfTheTemplate() throws Exception {
        long visitorId = startDemoSession().userId();

        assertThat(count("charging_plans", "user_id", visitorId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM charging_plan_slots sl
                JOIN charging_plans p ON p.id = sl.charging_plan_id WHERE p.user_id = ?
                """, Integer.class, visitorId)).isEqualTo(4);
        List<String> scheduleStatuses = jdbcTemplate.queryForList("""
                SELECT s.status::text FROM charging_schedules s
                JOIN charging_plans p ON p.id = s.charging_plan_id WHERE p.user_id = ? ORDER BY s.status
                """, String.class, visitorId);
        assertThat(scheduleStatuses).containsExactly("COMPLETED", "FAILED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM charging_sessions ss
                JOIN charging_schedules s ON s.id = ss.charging_schedule_id
                JOIN charging_plans p ON p.id = s.charging_plan_id WHERE p.user_id = ?
                """, Integer.class, visitorId)).isEqualTo(2);
    }

    @Test
    void theCopiedHistoryBelongsToTheVisitorsOwnEvAndKeepsEveryValue() throws Exception {
        long visitorId = startDemoSession().userId();

        Long visitorEvId = jdbcTemplate.queryForObject("SELECT id FROM evs WHERE user_id = ?", Long.class, visitorId);
        List<Long> evIdsOnPlans = jdbcTemplate.queryForList(
                "SELECT DISTINCT ev_id FROM charging_plans WHERE user_id = ?", Long.class, visitorId);
        assertThat(evIdsOnPlans).containsExactly(visitorEvId);

        // Same values, same timestamps: the history is ordered by created_at, so a copy stamped with
        // the time of the copy would reorder it.
        String compare = """
                SELECT p.created_at, p.updated_at, p.estimated_cost_nok, p.recommended_start_at, s.created_at,
                       s.status::text, ss.created_at, ss.status::text, ss.actual_cost_nok, ss.failure_code
                FROM charging_plans p
                JOIN charging_schedules s ON s.charging_plan_id = p.id
                JOIN charging_sessions ss ON ss.charging_schedule_id = s.id
                WHERE p.user_id = ?
                  AND ss.status IN ('COMPLETED', 'FAILED')
                ORDER BY p.created_at DESC
                """;
        List<String> template = jdbcTemplate.query(compare, (rs, i) -> rowSummary(rs), templateUserId);
        List<String> visitor = jdbcTemplate.query(compare, (rs, i) -> rowSummary(rs), visitorId);
        assertThat(visitor).hasSize(2).isEqualTo(template);
    }

    @Test
    void theHistoryEndpointShowsTheCopiedChargesNewestFirst() throws Exception {
        DemoSession session = startDemoSession();

        mockMvc.perform(get("/api/v1/charging-history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.content[1].status").value("FAILED"))
                .andExpect(jsonPath("$.content[1].failureCode").value("CHARGER_UNAVAILABLE"));
    }

    @Test
    void theTemplateKeepsAllItsOwnChargingData() throws Exception {
        startDemoSession();

        assertThat(count("charging_plans", "user_id", templateUserId)).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM charging_schedules s
                JOIN charging_plans p ON p.id = s.charging_plan_id WHERE p.user_id = ?
                """, Integer.class, templateUserId)).isEqualTo(5);
    }

    private record DemoSession(long userId, String accessToken) {
    }

    private DemoSession startDemoSession() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/demo"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new DemoSession(((Number) JsonPath.read(body, "$.user.id")).longValue(), JsonPath.read(body, "$.accessToken"));
    }

    private static String rowSummary(ResultSet rs) throws SQLException {
        StringBuilder summary = new StringBuilder();
        for (int column = 1; column <= rs.getMetaData().getColumnCount(); column++) {
            summary.append(rs.getString(column)).append('|');
        }
        return summary.toString();
    }

    private int count(String table, String column, long id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM %s WHERE %s = ?".formatted(table, column), Integer.class, id);
        return count == null ? 0 : count;
    }

    /** One plan with its two slots, its schedule and, for states that have run, its session. */
    private void insertCharge(long evId, long priceId, int daysAgo, String scheduleStatus, String sessionStatus) {
        OffsetDateTime created = OffsetDateTime.now(ZoneOffset.UTC).minusDays(daysAgo).truncatedTo(ChronoUnit.MICROS);
        OffsetDateTime start = created.plusHours(1);
        OffsetDateTime end = created.plusHours(6);

        Long planId = jdbcTemplate.queryForObject("""
                INSERT INTO charging_plans (user_id, ev_id, current_battery_percent, target_battery_percent, price_area,
                    earliest_start_at, required_completion_at, ev_name, ev_manufacturer, ev_model, battery_capacity_kwh,
                    max_ac_charging_power_kw, default_charger_power_kw, calculated_energy_kwh, effective_charging_power_kw,
                    estimated_duration_minutes, recommended_start_at, recommended_end_at, expected_energy_kwh,
                    estimated_cost_nok, baseline_cost_nok, expected_savings_nok, status, created_at, updated_at)
                VALUES (?, ?, 20, 80, 'NO1', ?, ?, 'Demo i4', 'BMW', 'i4 eDrive40', 81.1, 11, 7.4, 40, 7.4, 330,
                    ?, ?, 40, 20.5, 25.5, 5.0, 'SUCCEEDED', ?, ?)
                RETURNING id
                """, Long.class, templateUserId, evId, created, created.plusHours(12), start, end, created, created);

        for (int sequence = 1; sequence <= 2; sequence++) {
            jdbcTemplate.update("""
                    INSERT INTO charging_plan_slots (charging_plan_id, electricity_price_id, slot_start_at, slot_end_at,
                        price_per_kwh, planned_energy_kwh, expected_cost_nok, sequence_no)
                    VALUES (?, ?, ?, ?, 0.5, 7.4, 3.7, ?)
                    """, planId, priceId, start.plusHours(sequence - 1), start.plusHours(sequence), sequence);
        }

        Long scheduleId = jdbcTemplate.queryForObject("""
                INSERT INTO charging_schedules (charging_plan_id, scheduled_start_at, scheduled_end_at, expected_energy_kwh,
                    estimated_cost_nok, status, created_at, updated_at)
                VALUES (?, ?, ?, 40, 20.5, CAST(? AS charging_schedule_status), ?, ?)
                RETURNING id
                """, Long.class, planId, start, end, scheduleStatus, created, created);

        if ("COMPLETED".equals(sessionStatus)) {
            jdbcTemplate.update("""
                    INSERT INTO charging_sessions (charging_schedule_id, started_at, completed_at, actual_energy_kwh,
                        actual_cost_nok, baseline_cost_nok, optimized_cost_nok, estimated_savings_nok, status,
                        created_at, updated_at)
                    VALUES (?, ?, ?, 40, 20.5, 25.5, 20.5, 5.0, CAST('COMPLETED' AS charging_session_status), ?, ?)
                    """, scheduleId, start, end, created, created);
        } else if ("FAILED".equals(sessionStatus)) {
            jdbcTemplate.update("""
                    INSERT INTO charging_sessions (charging_schedule_id, status, failure_code, failure_reason,
                        created_at, updated_at)
                    VALUES (?, CAST('FAILED' AS charging_session_status), 'CHARGER_UNAVAILABLE',
                        'The charger is unavailable.', ?, ?)
                    """, scheduleId, created, created);
        } else if ("STARTED".equals(sessionStatus)) {
            jdbcTemplate.update("""
                    INSERT INTO charging_sessions (charging_schedule_id, started_at, status, created_at, updated_at)
                    VALUES (?, ?, CAST('STARTED' AS charging_session_status), ?, ?)
                    """, scheduleId, start, created, created);
        }
    }
}
