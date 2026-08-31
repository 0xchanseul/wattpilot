package com.wattpilot.auth.controller;

import com.wattpilot.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Request validation only; the filter chain is disabled because authorization is covered
 * end to end by {@code AuthApiIntegrationTest}.
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerValidationTest {

    private static final String WEAK_PASSWORD = "short11";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void signUpRejectsAPasswordBelowTheMinimumLength() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"iris@example.com","password":"%s","name":"Iris","defaultPriceArea":"NO1"}
                                """.formatted(WEAK_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
    }

    @Test
    void aRejectedPasswordIsNeverEchoedBackInTheErrorResponse() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"iris@example.com","password":"%s","name":"Iris","defaultPriceArea":"NO1"}
                                """.formatted(WEAK_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString(WEAK_PASSWORD))))
                .andExpect(jsonPath("$.fieldErrors[0].rejectedValue").doesNotExist());
    }

    @Test
    void aRejectedNonSensitiveValueIsStillReportedBack() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"long-enough-password","name":"Iris","defaultPriceArea":"NO1"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"))
                .andExpect(jsonPath("$.fieldErrors[0].rejectedValue").value("not-an-email"));
    }

    @Test
    void loginRequiresBothCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"iris@example.com"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
