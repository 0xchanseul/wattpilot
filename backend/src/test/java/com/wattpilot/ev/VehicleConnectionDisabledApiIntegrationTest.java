package com.wattpilot.ev;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code wattpilot.integration.smartcar.enabled} is false by default in every profile (see
 * application.yml), unlike {@link VehicleConnectionApiIntegrationTest} which enables it. The
 * property is pinned to false here explicitly (rather than relying on that default) because the
 * local profile imports the repository-root {@code .env} file (see application-local.yml), and a
 * developer who has followed docs/deployment.md to set up a real Smartcar account locally would
 * otherwise have that {@code .env} silently flip this test's assumption.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "wattpilot.integration.smartcar.enabled=false")
class VehicleConnectionDisabledApiIntegrationTest {

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    private static final AtomicInteger EMAIL_SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void connectUrlIsUnavailableWhenSmartcarIsNotConfigured() throws Exception {
        String token = signUpAndToken();
        long evId = createEvAndReturnId(token);

        mockMvc.perform(post("/api/v1/evs/" + evId + "/vehicle-connection/connect-url")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("VEHICLE_PROVIDER_NOT_CONFIGURED"));
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

    private long createEvAndReturnId(String token) throws Exception {
        String body = mockMvc.perform(post("/api/v1/evs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"My i4","manufacturer":"BMW","model":"i4 eDrive40","batteryCapacityKwh":81.1,"maxAcChargingPowerKw":11,"defaultChargerPowerKw":7.4}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private static String nextEmail() {
        return "vehicle-connection-disabled-user%d@example.com".formatted(EMAIL_SEQUENCE.incrementAndGet());
    }
}
