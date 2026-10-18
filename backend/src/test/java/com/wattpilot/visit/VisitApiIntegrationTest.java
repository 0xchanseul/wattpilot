package com.wattpilot.visit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class VisitApiIntegrationTest {

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    private static final String BROWSER_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) Chrome/130.0 Safari/537.36";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void repeatVisitsFromTheSameVisitorCountOnceAndNeedNoToken() throws Exception {
        jdbcTemplate.update("DELETE FROM daily_visitors");

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/visits")
                            .header("X-Real-IP", "203.0.113.7")
                            .header(HttpHeaders.USER_AGENT, BROWSER_USER_AGENT))
                    .andExpect(status().isNoContent());
        }
        mockMvc.perform(post("/api/v1/visits")
                        .header("X-Real-IP", "203.0.113.8")
                        .header(HttpHeaders.USER_AGENT, BROWSER_USER_AGENT))
                .andExpect(status().isNoContent());

        assertEquals(2, jdbcTemplate.queryForObject("SELECT count(*) FROM daily_visitors", Integer.class));
    }

    @Test
    void botVisitsAreAcknowledgedButNotStored() throws Exception {
        jdbcTemplate.update("DELETE FROM daily_visitors");

        mockMvc.perform(post("/api/v1/visits")
                        .header("X-Real-IP", "198.51.100.1")
                        .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (compatible; bingbot/2.0)"))
                .andExpect(status().isNoContent());

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM daily_visitors", Integer.class));
    }
}
