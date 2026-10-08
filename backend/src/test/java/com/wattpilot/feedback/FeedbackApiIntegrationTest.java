package com.wattpilot.feedback;

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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class FeedbackApiIntegrationTest {

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    private static final String EMAIL = "feedback-sender@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void feedbackIsStoredWithTheSendersEmailAndATrimmedMessage() throws Exception {
        String token = signUpAndToken();

        mockMvc.perform(sendFeedback(token, "  Nice work!  "))
                .andExpect(status().isNoContent());

        assertEquals("Nice work!", jdbcTemplate.queryForObject(
                "SELECT message FROM feedback WHERE email = ?", String.class, EMAIL));
    }

    @Test
    void blankOrTooLongMessagesAreRejected() throws Exception {
        String token = signUpAndToken();

        mockMvc.perform(sendFeedback(token, "   ")).andExpect(status().isBadRequest());
        mockMvc.perform(sendFeedback(token, "x".repeat(2001))).andExpect(status().isBadRequest());
    }

    @Test
    void feedbackBeyondTheDailyLimitIsRejectedWithTooManyRequests() throws Exception {
        String limitedEmail = "feedback-flood@example.com";
        String body = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"wattpilot-secret","name":"Flood","defaultPriceArea":"NO1"}
                                """.formatted(limitedEmail)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(sendFeedback(token, "message " + i)).andExpect(status().isNoContent());
        }

        mockMvc.perform(sendFeedback(token, "one too many"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("FEEDBACK_LIMIT_REACHED"));
    }

    @Test
    void sendingFeedbackRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.RequestBuilder sendFeedback(String token, String message) {
        return post("/api/v1/feedback")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"%s\"}".formatted(message));
    }

    private String signUpAndToken() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"wattpilot-secret","name":"Iris","defaultPriceArea":"NO1"}
                                """.formatted(EMAIL)))
                .andReturn().getResponse().getContentAsString();
        if (body.isBlank() || !body.contains("accessToken")) {
            body = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"%s\",\"password\":\"wattpilot-secret\"}".formatted(EMAIL)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
        }
        return JsonPath.read(body, "$.accessToken");
    }
}
