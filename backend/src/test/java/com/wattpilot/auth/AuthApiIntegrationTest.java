package com.wattpilot.auth;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the auth endpoints against a real PostgreSQL instance, so the Flyway schema, the
 * entity mappings and the security filter chain are all verified together.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthApiIntegrationTest {

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    private static final AtomicInteger EMAIL_SEQUENCE = new AtomicInteger();
    private static final String REFRESH_COOKIE = "wp_refresh_token";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void signUpReturnsTheCreatedUserWithAnAccessTokenAndARefreshCookie() throws Exception {
        String email = nextEmail();

        mockMvc.perform(signUp(email, "wattpilot-secret"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.status").value("ACTIVE"))
                .andExpect(jsonPath("$.user.defaultPriceArea").value("NO1"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                // The refresh token is delivered only as an HttpOnly cookie, never in the body.
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(cookie().exists(REFRESH_COOKIE))
                .andExpect(cookie().httpOnly(REFRESH_COOKIE, true))
                .andExpect(cookie().secure(REFRESH_COOKIE, true))
                .andExpect(cookie().path(REFRESH_COOKIE, "/api/v1/auth"))
                .andExpect(cookie().sameSite(REFRESH_COOKIE, "Lax"));
    }

    @Test
    void signingUpTwiceWithTheSameEmailConflicts() throws Exception {
        String email = nextEmail();
        mockMvc.perform(signUp(email, "wattpilot-secret")).andExpect(status().isCreated());

        mockMvc.perform(signUp(email.toUpperCase(), "wattpilot-secret"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void currentUserRequiresAToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void currentUserIsResolvedFromTheAccessToken() throws Exception {
        String email = nextEmail();
        String body = signUpAndReturnResponse(email).getContentAsString();

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + JsonPath.read(body, "$.accessToken")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.name").value("Iris"));
    }

    @Test
    void aMalformedTokenIsRejectedAsInvalidRatherThanMissing() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void loginWithTheWrongPasswordIsRejected() throws Exception {
        String email = nextEmail();
        mockMvc.perform(signUp(email, "wattpilot-secret")).andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"wrong-password"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void refreshWithoutACookieIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void refreshRotatesTheTokenAndTheOldOneStopsWorking() throws Exception {
        String firstRefreshToken = refreshCookieValue(signUpAndReturnResponse(nextEmail()));

        MockHttpServletResponse rotated = mockMvc.perform(refresh(firstRefreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn().getResponse();

        String secondRefreshToken = refreshCookieValue(rotated);
        assertThat(secondRefreshToken).isNotEqualTo(firstRefreshToken);

        mockMvc.perform(refresh(firstRefreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        mockMvc.perform(refresh(secondRefreshToken)).andExpect(status().isOk());
    }

    @Test
    void logoutRevokesTheRefreshTokenClearsTheCookieAndIsRepeatable() throws Exception {
        MockHttpServletResponse issued = signUpAndReturnResponse(nextEmail());
        String accessToken = JsonPath.read(issued.getContentAsString(), "$.accessToken");
        String refreshToken = refreshCookieValue(issued);

        mockMvc.perform(logout(accessToken, refreshToken))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(REFRESH_COOKIE, 0));
        mockMvc.perform(logout(accessToken, refreshToken)).andExpect(status().isNoContent());

        mockMvc.perform(refresh(refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void oneUserCannotLogOutAnotherUsersSession() throws Exception {
        String victimRefreshToken = refreshCookieValue(signUpAndReturnResponse(nextEmail()));
        String attackerAccessToken = JsonPath.read(
                signUpAndReturnResponse(nextEmail()).getContentAsString(), "$.accessToken");

        mockMvc.perform(logout(attackerAccessToken, victimRefreshToken)).andExpect(status().isNoContent());

        // The victim session is untouched, so its token still refreshes.
        mockMvc.perform(refresh(victimRefreshToken)).andExpect(status().isOk());
    }

    @Test
    void logoutRequiresAnAccessToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(REFRESH_COOKIE, "anything")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    /**
     * The frontend dev server runs on a different origin, so a failed preflight would break every
     * call from the browser while leaving the API itself working.
     */
    @Test
    void aPreflightFromTheConfiguredFrontendOriginIsAllowed() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                // The refresh cookie is only sent when the browser is told credentialed requests are allowed.
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void aPreflightFromAnUnknownOriginIsRefused() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    private MockHttpServletResponse signUpAndReturnResponse(String email) throws Exception {
        return mockMvc.perform(signUp(email, "wattpilot-secret"))
                .andExpect(status().isCreated())
                .andReturn().getResponse();
    }

    private static String refreshCookieValue(MockHttpServletResponse response) {
        Cookie cookie = response.getCookie(REFRESH_COOKIE);
        assertThat(cookie).as("refresh token cookie").isNotNull();
        return cookie.getValue();
    }

    private static RequestBuilder signUp(String email, String password) {
        return post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s","name":"Iris","defaultPriceArea":"NO1"}
                        """.formatted(email, password));
    }

    private static RequestBuilder refresh(String refreshToken) {
        return post("/api/v1/auth/refresh").cookie(new Cookie(REFRESH_COOKIE, refreshToken));
    }

    private static RequestBuilder logout(String accessToken, String refreshToken) {
        return post("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .cookie(new Cookie(REFRESH_COOKIE, refreshToken));
    }

    private static String nextEmail() {
        return "iris%d@example.com".formatted(EMAIL_SEQUENCE.incrementAndGet());
    }
}
