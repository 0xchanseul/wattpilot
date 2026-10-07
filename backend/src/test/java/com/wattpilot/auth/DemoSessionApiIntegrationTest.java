package com.wattpilot.auth;

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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the demo login against a real PostgreSQL instance with a template account that has one
 * locked EV and a Smartcar connection, so the copy semantics, the demo flag and the security filter
 * chain are verified together.
 */
@SpringBootTest(properties = {
        "wattpilot.demo.enabled=true",
        "wattpilot.demo.template-email=demo-template@example.com",
        "wattpilot.demo.ttl=6h"
})
@AutoConfigureMockMvc
@Testcontainers
class DemoSessionApiIntegrationTest {

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    private static final String TEMPLATE_EMAIL = "demo-template@example.com";
    private static final String REFRESH_COOKIE = "wp_refresh_token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void ensureTemplateAccount() throws Exception {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE email = ?", Integer.class, TEMPLATE_EMAIL);
        if (existing != null && existing > 0) {
            return;
        }
        String body = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"wattpilot-secret","name":"Template","defaultPriceArea":"NO3"}
                                """.formatted(TEMPLATE_EMAIL)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");
        String evBody = mockMvc.perform(post("/api/v1/evs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Demo i4","manufacturer":"BMW","model":"i4 eDrive40","batteryCapacityKwh":81.1,"maxAcChargingPowerKw":11,"defaultChargerPowerKw":7.4}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long evId = ((Number) JsonPath.read(evBody, "$.id")).longValue();
        jdbcTemplate.update("UPDATE evs SET locked = true WHERE id = ?", evId);
        jdbcTemplate.update("""
                INSERT INTO vehicle_connections (user_id, ev_id, smartcar_user_id, smartcar_vehicle_id,
                    smartcar_connection_id, vehicle_make, vehicle_model, vehicle_year, created_at, updated_at)
                SELECT user_id, id, 'sc-user', 'sc-vehicle', 'sc-connection', 'TESLA', 'Model 3', 2022, now(), now()
                FROM evs WHERE id = ?
                """, evId);
    }

    @Test
    void startingADemoSessionCreatesAFlaggedAccountAndReturnsATokenAndRefreshCookie() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/demo"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(startsWith("demo-")))
                .andExpect(jsonPath("$.user.email").value(endsWith("@demo.wattpilot.invalid")))
                .andExpect(jsonPath("$.user.name").value("Demo visitor"))
                .andExpect(jsonPath("$.user.demo").value(true))
                .andExpect(jsonPath("$.user.defaultPriceArea").value("NO3"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(cookie().httpOnly(REFRESH_COOKIE, true))
                // The session is capped at the demo account lifetime (6h here), well under the 7d default.
                .andExpect(cookie().maxAge(REFRESH_COOKIE, 6 * 3600))
                .andReturn().getResponse().getContentAsString();

        String email = JsonPath.read(body, "$.user.email");
        Boolean demo = jdbcTemplate.queryForObject("SELECT demo FROM users WHERE email = ?", Boolean.class, email);
        assertThat(demo).isTrue();
    }

    @Test
    void theVisitorGetsALockedCopyOfTheTemplateEvWithItsSmartcarConnection() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/demo"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");
        long visitorId = ((Number) JsonPath.read(body, "$.user.id")).longValue();

        mockMvc.perform(get("/api/v1/evs").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Demo i4"))
                .andExpect(jsonPath("$.content[0].locked").value(true))
                .andExpect(jsonPath("$.content[0].batteryCapacityKwh").value(81.1));

        List<String> connection = jdbcTemplate.queryForList("""
                SELECT smartcar_user_id || '/' || smartcar_vehicle_id || '/' || smartcar_connection_id
                FROM vehicle_connections WHERE user_id = ?
                """, String.class, visitorId);
        assertThat(connection).containsExactly("sc-user/sc-vehicle/sc-connection");
    }

    @Test
    void theVisitorCannotEditTheirDemoEv() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/demo"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");
        long visitorId = ((Number) JsonPath.read(body, "$.user.id")).longValue();
        Long evId = jdbcTemplate.queryForObject("SELECT id FROM evs WHERE user_id = ?", Long.class, visitorId);

        mockMvc.perform(patch("/api/v1/evs/" + evId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Renamed"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EV_LOCKED"));
    }

    @Test
    void eachDemoSessionGetsItsOwnAccountAndEvAndTheTemplateIsLeftUntouched() throws Exception {
        long first = demoUserId();
        long second = demoUserId();

        assertThat(first).isNotEqualTo(second);
        List<Long> evOwners = jdbcTemplate.queryForList(
                "SELECT user_id FROM evs WHERE user_id IN (?, ?) ORDER BY user_id", Long.class, first, second);
        assertThat(evOwners).containsExactly(Math.min(first, second), Math.max(first, second));

        Integer templateEvs = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM evs e JOIN users u ON u.id = e.user_id WHERE u.email = ?
                """, Integer.class, TEMPLATE_EMAIL);
        Boolean templateIsDemo = jdbcTemplate.queryForObject(
                "SELECT demo FROM users WHERE email = ?", Boolean.class, TEMPLATE_EMAIL);
        assertThat(templateEvs).isEqualTo(1);
        assertThat(templateIsDemo).isFalse();
    }

    private long demoUserId() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/demo"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.user.id")).longValue();
    }
}
