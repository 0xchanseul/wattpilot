package com.wattpilot.integration.smartcar;

import com.wattpilot.integration.smartcar.dto.SmartcarTelemetry;
import com.wattpilot.integration.smartcar.dto.SmartcarVehicleCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SmartcarClientTest {

    private static final String VEHICLE_API_BASE = "https://vehicle.api.smartcar.com/v3";
    private static final String AUTH_URL = "https://iam.smartcar.com/oauth2/token";
    private static final String CONNECT_URL = "https://connect.smartcar.com/oauth/authorize";
    private static final String TOKEN_JSON =
            "{\"access_token\":\"app-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}";

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private SmartcarProperties properties;
    private Instant now;
    private Clock clock;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl(VEHICLE_API_BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        properties = new SmartcarProperties(true, "client-id", "client-secret", "app-id",
                "https://www.wattpilot.dev/vehicle-connections/callback", "simulated", CONNECT_URL, AUTH_URL,
                VEHICLE_API_BASE, Duration.ofSeconds(3), Duration.ofSeconds(5));
        now = Instant.parse("2026-09-28T10:00:00Z");
        clock = Clock.fixed(now, ZoneOffset.UTC);
    }

    private SmartcarClient client() {
        return new SmartcarClient(builder.build(), properties, clock);
    }

    @Test
    void buildsConnectUrlWithApplicationTokenResponseType() {
        String url = client().buildConnectUrl("the-state", "42");

        assertThat(url).startsWith(CONNECT_URL + "?");
        assertThat(url).contains("application_id=app-id");
        assertThat(url).contains("response_type=none");
        assertThat(url).contains("mode=simulated");
        assertThat(url).contains("scope=read_battery+read_charge");
        assertThat(url).contains("state=the-state");
        assertThat(url).contains("external_id=42");
    }

    @Test
    void fetchesAndCachesTheAppTokenAcrossMultipleCalls() {
        server.expect(requestTo(AUTH_URL)).andExpect(method(POST))
                .andRespond(withSuccess(TOKEN_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo(VEHICLE_API_BASE + "/connections?filter%5BuserId%5D=sc-user-1"))
                .andExpect(method(GET))
                .andExpect(header("Authorization", "Bearer app-token"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(VEHICLE_API_BASE + "/connections?filter%5BuserId%5D=sc-user-1"))
                .andExpect(method(GET))
                .andExpect(header("Authorization", "Bearer app-token"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        SmartcarClient client = client();
        client.listVehicles("sc-user-1");
        client.listVehicles("sc-user-1");

        // Only one token POST was expected above; a second would fail server.verify().
        server.verify();
    }

    @Test
    void renewsTheTokenOnceItIsWithinTheRenewalMargin() {
        server.expect(requestTo(AUTH_URL)).andExpect(method(POST))
                .andRespond(withSuccess(
                        "{\"access_token\":\"token-1\",\"token_type\":\"Bearer\",\"expires_in\":90}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(VEHICLE_API_BASE + "/connections?filter%5BuserId%5D=u")).andExpect(method(GET))
                .andExpect(header("Authorization", "Bearer token-1"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));
        // expires_in=90s and the renewal margin is 60s, so once "now" advances by 40s (50s left,
        // under the 60s margin) the next call must fetch a fresh token.
        server.expect(requestTo(AUTH_URL)).andExpect(method(POST))
                .andRespond(withSuccess(
                        "{\"access_token\":\"token-2\",\"token_type\":\"Bearer\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(VEHICLE_API_BASE + "/connections?filter%5BuserId%5D=u")).andExpect(method(GET))
                .andExpect(header("Authorization", "Bearer token-2"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        MutableClock mutableClock = new MutableClock(now);
        SmartcarClient client = new SmartcarClient(builder.build(), properties, mutableClock);
        client.listVehicles("u");
        mutableClock.advance(Duration.ofSeconds(40));
        client.listVehicles("u");

        server.verify();
    }

    @Test
    void listVehiclesFiltersOutConnectionsForADifferentSmartcarUser() {
        stubToken();
        server.expect(requestTo(VEHICLE_API_BASE + "/connections?filter%5BuserId%5D=sc-user-1"))
                .andRespond(withSuccess("""
                        {"data": [
                          {"id": "conn-1",
                            "attributes": {"user": {"id": "sc-user-1"},
                                           "vehicle": {"make": "Tesla", "model": "Model 3", "year": 2023}},
                            "relationships": {"vehicle": {"data": {"id": "veh-1", "type": "vehicle"}}}},
                          {"id": "conn-2",
                            "attributes": {"user": {"id": "some-other-user"},
                                           "vehicle": {"make": "Hyundai", "model": "Ioniq 5", "year": 2024}},
                            "relationships": {"vehicle": {"data": {"id": "veh-2", "type": "vehicle"}}}}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<SmartcarVehicleCandidate> vehicles = client().listVehicles("sc-user-1");

        assertThat(vehicles).hasSize(1);
        assertThat(vehicles.get(0).smartcarVehicleId()).isEqualTo("veh-1");
        assertThat(vehicles.get(0).connectionId()).isEqualTo("conn-1");
        assertThat(vehicles.get(0).make()).isEqualTo("Tesla");
    }

    @Test
    void readTelemetryCombinesFourIndependentSignalCalls() {
        stubToken();
        stubSignal("tractionbattery-stateofcharge", "\"value\": 78");
        stubSignal("tractionbattery-range", "\"value\": 310.5");
        stubSignal("charge-ischarging", "\"value\": true");
        stubSignal("charge-ischargingcableconnected", "\"value\": true");

        SmartcarTelemetry telemetry = client().readTelemetry("sc-user-1", "veh-1");

        assertThat(telemetry.stateOfChargePercent()).isEqualTo(78.0);
        assertThat(telemetry.rangeKm()).isEqualTo(310.5);
        assertThat(telemetry.isCharging()).isTrue();
        assertThat(telemetry.isPluggedIn()).isTrue();
    }

    @Test
    void anUnsupportedSignalBecomesNullInsteadOfFailingTheWholeRead() {
        stubToken();
        stubSignal("tractionbattery-stateofcharge", "\"value\": 55");
        server.expect(requestTo(VEHICLE_API_BASE + "/vehicles/veh-1/signals/tractionbattery-range"))
                .andRespond(withSuccess("""
                        {"data": {"id": "tractionbattery-range",
                                  "attributes": {"code": "tractionbattery-range",
                                                 "status": {"value": "SIGNAL_UNSUPPORTED"}}}}
                        """, MediaType.APPLICATION_JSON));
        stubSignal("charge-ischarging", "\"value\": false");
        stubSignal("charge-ischargingcableconnected", "\"value\": false");

        SmartcarTelemetry telemetry = client().readTelemetry("sc-user-1", "veh-1");

        assertThat(telemetry.stateOfChargePercent()).isEqualTo(55.0);
        assertThat(telemetry.rangeKm()).isNull();
        assertThat(telemetry.isCharging()).isFalse();
    }

    @Test
    void removeConnectionSendsADeleteWithTheAppToken() {
        stubToken();
        server.expect(requestTo(VEHICLE_API_BASE + "/connections/conn-1"))
                .andExpect(method(DELETE))
                .andExpect(header("Authorization", "Bearer app-token"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        client().removeConnection("conn-1");

        server.verify();
    }

    @Test
    void a401OnTheTokenRequestMapsToAProviderException() {
        server.expect(requestTo(AUTH_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client().listVehicles("u")).isInstanceOf(SmartcarProviderException.class);
    }

    @Test
    void aTransportFailureMapsToAProviderException() {
        stubToken();
        server.expect(requestTo(VEHICLE_API_BASE + "/connections?filter%5BuserId%5D=u"))
                .andRespond(withException(new SocketTimeoutException("read timed out")));

        assertThatThrownBy(() -> client().listVehicles("u")).isInstanceOf(SmartcarProviderException.class);
    }

    private void stubToken() {
        server.expect(requestTo(AUTH_URL)).andExpect(method(POST))
                .andRespond(withSuccess(TOKEN_JSON, MediaType.APPLICATION_JSON));
    }

    private void stubSignal(String code, String valueField) {
        server.expect(requestTo(VEHICLE_API_BASE + "/vehicles/veh-1/signals/" + code))
                .andExpect(method(GET))
                .andExpect(header("sc-user-id", "sc-user-1"))
                .andRespond(withSuccess("""
                        {"data": {"id": "%s", "attributes": {"code": "%s",
                                  "status": {"value": "SUCCESS"}, "body": {%s, "unit": "n/a"}}}}
                        """.formatted(code, code, valueField), MediaType.APPLICATION_JSON));
    }

    /** A {@link Clock} the test can advance mid-test, since {@link Clock#fixed} cannot change. */
    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
