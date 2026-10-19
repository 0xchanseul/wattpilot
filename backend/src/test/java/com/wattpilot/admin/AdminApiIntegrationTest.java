package com.wattpilot.admin;

import com.wattpilot.common.security.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AdminApiIntegrationTest {

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    private static final String ADMIN_PASSWORD = "correct-admin-password";
    private static final String TEMPLATE_EMAIL = "template@example.com";

    @DynamicPropertySource
    static void adminProperties(DynamicPropertyRegistry registry) {
        // Supplied without the {bcrypt} prefix on purpose: a bare hash from `htpasswd`-style tools must work.
        registry.add("wattpilot.admin.password-hash", () -> new BCryptPasswordEncoder().encode(ADMIN_PASSWORD));
        registry.add("wattpilot.demo.template-email", () -> TEMPLATE_EMAIL);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void cleanTables() {
        jdbc.update("DELETE FROM charging_schedules");
        jdbc.update("DELETE FROM charging_plans");
        jdbc.update("DELETE FROM evs");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM daily_visitors");
    }

    // --- access control ---

    @Test
    void adminEndpointsRequireTheAdminCookie() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/session")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordIsRejectedWithoutACookie() throws Exception {
        mockMvc.perform(post("/api/v1/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ADMIN_PASSWORD"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void loginSetsAStrictHttpOnlyCookieThatOpensTheAdminApi() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/v1/admin")))
                .andReturn();

        Cookie cookie = login.getResponse().getCookie("wp_admin_session");
        assertNotNull(cookie);

        mockMvc.perform(get("/api/v1/admin/session").cookie(cookie)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/admin/analytics").cookie(cookie)).andExpect(status().isOk());
    }

    @Test
    void aUserAccessTokenCannotOpenTheAdminApi() throws Exception {
        String userToken = jwtTokenProvider.createAccessToken(1L);

        mockMvc.perform(get("/api/v1/admin/analytics").header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
        // The same token presented in the admin cookie is no better.
        mockMvc.perform(get("/api/v1/admin/analytics").cookie(new Cookie("wp_admin_session", userToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anAdminCookieDoesNotAuthenticateUserEndpoints() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andReturn();
        Cookie cookie = login.getResponse().getCookie("wp_admin_session");

        mockMvc.perform(get("/api/v1/users/me").cookie(cookie)).andExpect(status().isUnauthorized());
        // The admin token has no numeric subject, so it is no use as a bearer token either.
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + cookie.getValue()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutClearsTheCookie() throws Exception {
        mockMvc.perform(post("/api/v1/admin/logout"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
    }

    // --- aggregates ---

    @Test
    void countsRealUsersOnlyAndBreaksVisitorsDownBySourceAndCountry() throws Exception {
        long alice = insertUser("alice@example.com", false, "now()");
        long bob = insertUser("bob@example.com", false, "now()");
        long demo = insertUser("demo-1@example.com", true, "now()");
        long template = insertUser(TEMPLATE_EMAIL, false, "now()");
        insertUser("old@example.com", false, "now() - interval '40 days'");

        insertSchedule(alice, "now()");
        insertSchedule(alice, "now()");
        insertSchedule(demo, "now()");
        insertSchedule(template, "now()");
        // Keeps the otherwise unused id from being optimised away by readers of this test.
        assertNotNull(bob);

        LocalDate today = LocalDate.now(ZoneId.of("Europe/Oslo"));
        insertVisitor(today, "a", "linkedin", "NO");
        insertVisitor(today, "b", "linkedin", "NO");
        insertVisitor(today, "c", "reddit", "KR");
        insertVisitor(today, "d", null, null);

        Cookie cookie = loginCookie();
        mockMvc.perform(get("/api/v1/admin/analytics").param("from", today.toString()).param("to", today.toString())
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.visitorDays").value(4))
                .andExpect(jsonPath("$.summary.newSignups").value(2))
                .andExpect(jsonPath("$.summary.totalSignups").value(3))
                .andExpect(jsonPath("$.summary.signupsPer100VisitorDays").value(50.0))
                .andExpect(jsonPath("$.summary.schedulesCreated").value(2))
                .andExpect(jsonPath("$.summary.usersWithSchedule").value(1))
                .andExpect(jsonPath("$.summary.totalUsers").value(3))
                .andExpect(jsonPath("$.summary.activationRate").value(0.3333))
                .andExpect(jsonPath("$.sources[0].source").value("linkedin"))
                .andExpect(jsonPath("$.sources[0].visitors").value(2))
                .andExpect(jsonPath("$.sources[0].share").value(0.5))
                .andExpect(jsonPath("$.countries[0].countryCode").value("NO"))
                .andExpect(jsonPath("$.countries[0].name").value("Norway"))
                .andExpect(jsonPath("$.daily.length()").value(1))
                .andExpect(jsonPath("$.daily[0].visitors").value(4))
                .andExpect(jsonPath("$.daily[0].signups").value(2))
                .andExpect(jsonPath("$.daily[0].schedules").value(2));
    }

    @Test
    void anEmptyDatabaseGivesZerosAndNoRates() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics").cookie(loginCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.visitorDays").value(0))
                .andExpect(jsonPath("$.summary.signupsPer100VisitorDays").doesNotExist())
                .andExpect(jsonPath("$.summary.activationRate").doesNotExist())
                .andExpect(jsonPath("$.sources.length()").value(0));
    }

    @Test
    void theDailySeriesIsZeroFilledAcrossTheRange() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Oslo"));
        insertVisitor(today.minusDays(2), "a", "direct", "NO");

        mockMvc.perform(get("/api/v1/admin/analytics")
                        .param("from", today.minusDays(3).toString()).param("to", today.toString())
                        .cookie(loginCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daily.length()").value(4))
                .andExpect(jsonPath("$.daily[0].visitors").value(0))
                .andExpect(jsonPath("$.daily[1].visitors").value(1))
                .andExpect(jsonPath("$.daily[3].visitors").value(0));
    }

    @Test
    void visitsFromBeforeTrackingShowAsAnUnknownSource() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Oslo"));
        insertVisitor(today, "a", null, null);

        mockMvc.perform(get("/api/v1/admin/analytics").param("from", today.toString()).cookie(loginCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources[0].source").doesNotExist())
                .andExpect(jsonPath("$.countries[0].name").value("Unknown"));
    }

    @Test
    void aRangeEndingBeforeItStartsIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics")
                        .param("from", "2026-10-10").param("to", "2026-10-01")
                        .cookie(loginCookie()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_TIME_RANGE"));
    }

    // --- helpers ---

    private Cookie loginCookie() throws Exception {
        return mockMvc.perform(post("/api/v1/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andReturn().getResponse().getCookie("wp_admin_session");
    }

    private long insertUser(String email, boolean demo, String createdAtSql) {
        return jdbc.queryForObject("INSERT INTO users (email, password_hash, name, default_price_area, status, demo, "
                + "created_at, updated_at) VALUES (?, 'x', 'Test', 'NO1', 'ACTIVE', ?, " + createdAtSql + ", now()) "
                + "RETURNING id", Long.class, email, demo);
    }

    /** A failed plan is the smallest valid plan row; the statistics only need its owner and the schedule's date. */
    private void insertSchedule(long userId, String createdAtSql) {
        long evId = jdbc.queryForObject("INSERT INTO evs (user_id, name, manufacturer, model, battery_capacity_kwh, "
                + "max_ac_charging_power_kw, default_charger_power_kw, status, created_at, updated_at) "
                + "VALUES (?, 'Car', 'M', 'X', 60, 11, 11, 'ACTIVE', now(), now()) RETURNING id", Long.class, userId);
        long planId = jdbc.queryForObject("INSERT INTO charging_plans (ev_id, user_id, current_battery_percent, "
                + "target_battery_percent, price_area, earliest_start_at, required_completion_at, ev_name, "
                + "ev_manufacturer, ev_model, battery_capacity_kwh, max_ac_charging_power_kw, "
                + "default_charger_power_kw, status, failure_reason, created_at, updated_at) "
                + "VALUES (?, ?, 20, 80, 'NO1', now(), now() + interval '8 hours', 'Car', 'M', 'X', 60, 11, 11, "
                + "'FAILED', 'test', now(), now()) RETURNING id", Long.class, evId, userId);
        jdbc.update("INSERT INTO charging_schedules (charging_plan_id, scheduled_start_at, scheduled_end_at, "
                + "expected_energy_kwh, estimated_cost_nok, status, created_at, updated_at) "
                + "VALUES (?, now(), now() + interval '2 hours', 10, 5, 'WAITING', " + createdAtSql + ", now())",
                planId);
    }

    private void insertVisitor(LocalDate day, String hashSeed, String source, String country) {
        jdbc.update("INSERT INTO daily_visitors (visit_date, visitor_hash, source, country_code) VALUES (?, ?, ?, ?)",
                day, hashSeed.repeat(64).substring(0, 64), source, country);
    }
}
