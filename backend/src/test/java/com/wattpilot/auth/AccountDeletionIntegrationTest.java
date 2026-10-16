package com.wattpilot.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AccountDeletionIntegrationTest {

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    private static final String PASSWORD = "wattpilot-secret";
    private static final String REFRESH_COOKIE = "wp_refresh_token";
    private static final AtomicInteger EMAIL_SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void deletingTheAccountRemovesTheUserItsEvsAndItsSessions() throws Exception {
        String email = nextEmail();
        String token = signUp(email);
        Long userId = userId(email);
        mockMvc.perform(createEv(token)).andExpect(status().isCreated());
        assertThat(count("evs", userId)).isEqualTo(1);
        assertThat(count("refresh_tokens", userId)).isEqualTo(1);

        mockMvc.perform(deleteAccount(token, PASSWORD))
                .andExpect(status().isNoContent())
                // The browser must not keep sending the stale refresh token.
                .andExpect(cookie().maxAge(REFRESH_COOKIE, 0));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class, userId))
                .isZero();
        assertThat(count("evs", userId)).isZero();
        assertThat(count("refresh_tokens", userId)).isZero();
    }

    @Test
    void theEmailCanBeRegisteredAgainAfterDeletion() throws Exception {
        String email = nextEmail();
        String token = signUp(email);

        mockMvc.perform(deleteAccount(token, PASSWORD)).andExpect(status().isNoContent());

        mockMvc.perform(signUpRequest(email)).andExpect(status().isCreated());
    }

    @Test
    void aWrongPasswordIsRejectedAndNothingIsDeleted() throws Exception {
        String email = nextEmail();
        String token = signUp(email);

        // 403 and not 401, so the client does not mistake it for an expired session.
        mockMvc.perform(deleteAccount(token, "not-the-password"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INCORRECT_PASSWORD"));

        assertThat(userId(email)).isNotNull();
    }

    @Test
    void deletingTheAccountRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/auth/delete-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenOfADeletedAccountNoLongerResolvesAUser() throws Exception {
        String token = signUp(nextEmail());
        mockMvc.perform(deleteAccount(token, PASSWORD)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void anotherAccountsDataIsUntouched() throws Exception {
        String keeperEmail = nextEmail();
        String keeperToken = signUp(keeperEmail);
        mockMvc.perform(createEv(keeperToken)).andExpect(status().isCreated());
        String leaverToken = signUp(nextEmail());

        mockMvc.perform(deleteAccount(leaverToken, PASSWORD)).andExpect(status().isNoContent());

        assertThat(count("evs", userId(keeperEmail))).isEqualTo(1);
    }

    private String signUp(String email) throws Exception {
        String body = mockMvc.perform(signUpRequest(email))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private RequestBuilder signUpRequest(String email) {
        return post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s","name":"Iris","defaultPriceArea":"NO1"}
                        """.formatted(email, PASSWORD));
    }

    private RequestBuilder createEv(String token) {
        return post("/api/v1/evs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"My EV","manufacturer":"Tesla","model":"Model 3","batteryCapacityKwh":60,
                         "maxAcChargingPowerKw":11,"defaultChargerPowerKw":7.4}
                        """);
    }

    private RequestBuilder deleteAccount(String token, String password) {
        return post("/api/v1/auth/delete-account")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"%s\"}".formatted(password));
    }

    private Long userId(String email) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private int count(String table, Long userId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table + " WHERE user_id = ?", Integer.class, userId);
    }

    private static String nextEmail() {
        return "delete-me-" + EMAIL_SEQUENCE.incrementAndGet() + "@example.com";
    }
}
