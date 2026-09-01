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
import org.springframework.test.web.servlet.RequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the EV endpoints against a real PostgreSQL instance, so the Flyway schema, the entity
 * mapping onto the {@code ev_status} enum, ownership scoping and the security filter chain are all
 * verified together.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class EvApiIntegrationTest {

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    private static final AtomicInteger EMAIL_SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registeringAnEvReturnsItWithALocationHeaderAndNoOwnerField() throws Exception {
        String token = signUpAndToken();

        mockMvc.perform(createEv(token, "My i4"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/v1/evs/")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("My i4"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.batteryCapacityKwh").value(81.1))
                .andExpect(jsonPath("$.userId").doesNotExist());
    }

    @Test
    void listingEvsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/evs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void anEvRegisteredByOneUserIsNotVisibleToAnother() throws Exception {
        long evId = createEvAndReturnId(signUpAndToken(), "Owner car");
        String stranger = signUpAndToken();

        mockMvc.perform(get("/api/v1/evs/" + evId).header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EV_NOT_FOUND"));
    }

    @Test
    void deactivatingAnEvHidesItFromTheDefaultListButKeepsItUnderTheInactiveFilter() throws Exception {
        String token = signUpAndToken();
        long evId = createEvAndReturnId(token, "My i4");

        mockMvc.perform(delete("/api/v1/evs/" + evId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/evs").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == %d)]".formatted(evId)).isEmpty());

        mockMvc.perform(get("/api/v1/evs")
                        .param("status", "INACTIVE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == %d)]".formatted(evId)).isNotEmpty());
    }

    @Test
    void aDeactivatedEvCanBeReactivatedThroughPatch() throws Exception {
        String token = signUpAndToken();
        long evId = createEvAndReturnId(token, "My i4");
        mockMvc.perform(delete("/api/v1/evs/" + evId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(patch("/api/v1/evs/" + evId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"ACTIVE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/evs").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.content[?(@.id == %d)]".formatted(evId)).isNotEmpty());
    }

    @Test
    void deactivatingAnAlreadyInactiveEvStillSucceeds() throws Exception {
        String token = signUpAndToken();
        long evId = createEvAndReturnId(token, "My i4");

        mockMvc.perform(delete("/api/v1/evs/" + evId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/evs/" + evId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void patchUpdatesOnlyTheProvidedFields() throws Exception {
        String token = signUpAndToken();
        long evId = createEvAndReturnId(token, "My i4");

        mockMvc.perform(patch("/api/v1/evs/" + evId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Weekend car","defaultChargerPowerKw":3.6}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Weekend car"))
                .andExpect(jsonPath("$.defaultChargerPowerKw").value(3.6))
                .andExpect(jsonPath("$.manufacturer").value("BMW"))
                .andExpect(jsonPath("$.maxAcChargingPowerKw").value(11));
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

    private long createEvAndReturnId(String token, String name) throws Exception {
        String body = mockMvc.perform(createEv(token, name))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private static RequestBuilder createEv(String token, String name) {
        return post("/api/v1/evs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","manufacturer":"BMW","model":"i4 eDrive40","batteryCapacityKwh":81.1,"maxAcChargingPowerKw":11,"defaultChargerPowerKw":7.4}
                        """.formatted(name));
    }

    private static String nextEmail() {
        return "ev-user%d@example.com".formatted(EMAIL_SEQUENCE.incrementAndGet());
    }
}
