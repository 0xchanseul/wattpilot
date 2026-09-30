package com.wattpilot.ev;

import com.jayway.jsonpath.JsonPath;
import com.wattpilot.integration.smartcar.SmartcarClient;
import com.wattpilot.integration.smartcar.SmartcarProviderException;
import com.wattpilot.integration.smartcar.dto.SmartcarTelemetry;
import com.wattpilot.integration.smartcar.dto.SmartcarVehicleCandidate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the full connect-url -> candidates -> link -> get -> telemetry -> disconnect flow
 * against a real PostgreSQL instance. {@link SmartcarClient} is mocked so no real Smartcar network
 * call is made; everything else (state signing/verification, ownership, persistence, the security
 * filter chain) is real.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "wattpilot.integration.smartcar.enabled=true",
        "wattpilot.integration.smartcar.client-id=test-client",
        "wattpilot.integration.smartcar.client-secret=test-secret",
        "wattpilot.integration.smartcar.application-id=test-app",
        "wattpilot.integration.smartcar.redirect-uri=https://example.com/vehicle-connections/callback"
})
class VehicleConnectionApiIntegrationTest {

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    private static final AtomicInteger EMAIL_SEQUENCE = new AtomicInteger();
    private static final SmartcarVehicleCandidate CANDIDATE =
            new SmartcarVehicleCandidate("conn-1", "veh-1", "Tesla", "Model 3", 2023);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SmartcarClient smartcarClient;

    @Test
    void connectUrlRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/evs/1/vehicle-connection/connect-url"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void connectUrlOnAnEvOwnedByAnotherUserIsNotFound() throws Exception {
        String ownerToken = signUpAndToken();
        long evId = createEvAndReturnId(ownerToken);
        String strangerToken = signUpAndToken();

        mockMvc.perform(post("/api/v1/evs/" + evId + "/vehicle-connection/connect-url")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EV_NOT_FOUND"));
    }

    @Test
    void fullConnectCandidatesLinkGetTelemetryDisconnectFlow() throws Exception {
        String token = signUpAndToken();
        long evId = createEvAndReturnId(token);
        when(smartcarClient.buildConnectUrl(any(), any()))
                .thenAnswer(invocation -> "https://connect.smartcar.com/oauth/authorize?state="
                        + invocation.getArgument(0) + "&external_id=" + invocation.getArgument(1));
        when(smartcarClient.listVehicles("sc-user-1")).thenReturn(List.of(CANDIDATE));

        String state = extractQueryParam(requestConnectUrl(token, evId), "state");

        mockMvc.perform(post("/api/v1/vehicle-connections/candidates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"state":"%s","smartcarUserId":"sc-user-1"}
                                """.formatted(state)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evId").value(evId))
                .andExpect(jsonPath("$.vehicles[0].smartcarVehicleId").value("veh-1"))
                .andExpect(jsonPath("$.vehicles[0].make").value("Tesla"));

        mockMvc.perform(post("/api/v1/evs/" + evId + "/vehicle-connection")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"state":"%s","smartcarUserId":"sc-user-1","smartcarVehicleId":"veh-1"}
                                """.formatted(state)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.evId").value(evId))
                .andExpect(jsonPath("$.make").value("Tesla"))
                .andExpect(jsonPath("$.model").value("Model 3"));

        mockMvc.perform(get("/api/v1/evs/" + evId + "/vehicle-connection")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.make").value("Tesla"));

        when(smartcarClient.readTelemetry("sc-user-1", "veh-1")).thenReturn(
                new SmartcarTelemetry(72.0, 305.5, true, true, OffsetDateTime.now()));
        mockMvc.perform(get("/api/v1/evs/" + evId + "/vehicle-connection/telemetry")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stateOfChargePercent").value(72.0))
                .andExpect(jsonPath("$.isPluggedIn").value(true));

        mockMvc.perform(delete("/api/v1/evs/" + evId + "/vehicle-connection")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/evs/" + evId + "/vehicle-connection")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VEHICLE_CONNECTION_NOT_FOUND"));
    }

    @Test
    void aSecondConnectUrlRequestOnAnAlreadyConnectedEvIsAConflict() throws Exception {
        String token = signUpAndToken();
        long evId = createEvAndReturnId(token);
        when(smartcarClient.buildConnectUrl(any(), any()))
                .thenAnswer(invocation -> "https://connect.smartcar.com/oauth/authorize?state=" + invocation.getArgument(0));
        when(smartcarClient.listVehicles("sc-user-1")).thenReturn(List.of(CANDIDATE));
        String state = extractQueryParam(requestConnectUrl(token, evId), "state");
        mockMvc.perform(post("/api/v1/evs/" + evId + "/vehicle-connection")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"state":"%s","smartcarUserId":"sc-user-1","smartcarVehicleId":"veh-1"}
                                """.formatted(state)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/evs/" + evId + "/vehicle-connection/connect-url")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VEHICLE_ALREADY_CONNECTED"));
    }

    @Test
    void aSmartcarFailureOnTelemetryMapsToABadGateway() throws Exception {
        String token = signUpAndToken();
        long evId = createEvAndReturnId(token);
        when(smartcarClient.buildConnectUrl(any(), any()))
                .thenAnswer(invocation -> "https://connect.smartcar.com/oauth/authorize?state=" + invocation.getArgument(0));
        when(smartcarClient.listVehicles("sc-user-1")).thenReturn(List.of(CANDIDATE));
        String state = extractQueryParam(requestConnectUrl(token, evId), "state");
        mockMvc.perform(post("/api/v1/evs/" + evId + "/vehicle-connection")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"state":"%s","smartcarUserId":"sc-user-1","smartcarVehicleId":"veh-1"}
                                """.formatted(state)))
                .andExpect(status().isCreated());
        when(smartcarClient.readTelemetry("sc-user-1", "veh-1"))
                .thenThrow(new SmartcarProviderException("boom"));

        mockMvc.perform(get("/api/v1/evs/" + evId + "/vehicle-connection/telemetry")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("VEHICLE_PROVIDER_UNAVAILABLE"));
    }

    private String requestConnectUrl(String token, long evId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/evs/" + evId + "/vehicle-connection/connect-url")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.url");
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
        return "vehicle-connection-user%d@example.com".formatted(EMAIL_SEQUENCE.incrementAndGet());
    }

    private static String extractQueryParam(String url, String name) {
        String marker = name + "=";
        int start = url.indexOf(marker) + marker.length();
        int end = url.indexOf('&', start);
        String raw = end == -1 ? url.substring(start) : url.substring(start, end);
        return java.net.URLDecoder.decode(raw, StandardCharsets.UTF_8);
    }
}
