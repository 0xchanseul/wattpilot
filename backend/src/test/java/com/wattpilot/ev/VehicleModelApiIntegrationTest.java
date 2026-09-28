package com.wattpilot.ev;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises {@code GET /vehicle-models} against a real PostgreSQL instance running the Flyway V6
 * seed data, so the migration's presets and the search filter are verified together.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class VehicleModelApiIntegrationTest {

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    private static final AtomicInteger EMAIL_SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listingRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/vehicle-models"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void listingWithNoQueryReturnsAllSeededPresetsOrderedByManufacturerThenModel() throws Exception {
        String token = signUpAndToken();

        mockMvc.perform(get("/api/v1/vehicle-models").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(10)))
                .andExpect(jsonPath("$[0].manufacturer").value("Audi"));
    }

    @Test
    void queryFiltersByManufacturerOrModelCaseInsensitively() throws Exception {
        String token = signUpAndToken();

        mockMvc.perform(get("/api/v1/vehicle-models")
                        .param("q", "tesla")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.manufacturer == 'Tesla')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.manufacturer != 'Tesla')]").isEmpty());
    }

    @Test
    void aPresetExposesTheSpecFieldsButNoDefaultChargerPower() throws Exception {
        String token = signUpAndToken();

        mockMvc.perform(get("/api/v1/vehicle-models")
                        .param("q", "Model Y")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].manufacturer").value("Tesla"))
                .andExpect(jsonPath("$[0].model").value("Model Y Long Range"))
                .andExpect(jsonPath("$[0].batteryCapacityKwh").value(75.0))
                .andExpect(jsonPath("$[0].maxAcChargingPowerKw").value(11.0))
                .andExpect(jsonPath("$[0].defaultChargerPowerKw").doesNotExist());
    }

    private String signUpAndToken() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"wattpilot-secret","name":"Iris","defaultPriceArea":"NO1"}
                                """.formatted(nextEmail())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private static String nextEmail() {
        return "vehicle-model-user%d@example.com".formatted(EMAIL_SEQUENCE.incrementAndGet());
    }
}
