package com.wattpilot.integration.smartcar;

import com.wattpilot.integration.smartcar.dto.SmartcarConnectionsEnvelope;
import com.wattpilot.integration.smartcar.dto.SmartcarSignalEnvelope;
import com.wattpilot.integration.smartcar.dto.SmartcarTelemetry;
import com.wattpilot.integration.smartcar.dto.SmartcarTokenResponse;
import com.wattpilot.integration.smartcar.dto.SmartcarVehicleCandidate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Client for the read-only parts of Smartcar API v3 (application access token, Connect URL,
 * vehicle signals, connections). Never requests {@code control_charge}: charging execution stays
 * on the Mock path (see {@code com.wattpilot.charging.adapter.MockChargingAdapter}); this client
 * only surfaces informational vehicle telemetry.
 *
 * <p>v3 has no per-vehicle OAuth tokens: one application-level token (client_credentials) is used
 * for every call, and a request is scoped to a specific Smartcar user by an id obtained from the
 * Connect redirect, not by a bearer token belonging to that user. Signal codes, the token/Connect
 * shapes, and the {@link #listVehicles} response shape were all confirmed live against a real
 * Smartcar simulated vehicle on 2026-09-29 (see TODO.md §3.3).
 */
@Component
public class SmartcarClient {

    private static final String SIGNAL_STATE_OF_CHARGE = "tractionbattery-stateofcharge";
    private static final String SIGNAL_RANGE = "tractionbattery-range";
    private static final String SIGNAL_IS_CHARGING = "charge-ischarging";
    private static final String SIGNAL_IS_PLUGGED_IN = "charge-ischargingcableconnected";
    private static final String HEADER_SC_USER_ID = "sc-user-id";
    // Explicit scope, so the grant never depends on the app's dashboard-configured "Vehicle Access"
    // default (Smartcar falls back to that default when `scope` is omitted from the Connect URL):
    // this integration is read-only and must never end up with control_charge or any other
    // permission beyond what it actually reads.
    private static final String CONNECT_SCOPE = "read_battery read_charge";
    // Renew the app token this long before it actually expires, so a slow request never gets
    // rejected mid-flight for a token that expired a moment earlier.
    private static final java.time.Duration TOKEN_RENEWAL_MARGIN = java.time.Duration.ofSeconds(60);

    private final RestClient restClient;
    private final SmartcarProperties properties;
    private final Clock clock;
    private volatile CachedToken cachedToken;

    @Autowired
    public SmartcarClient(RestClient smartcarRestClient, SmartcarProperties properties) {
        this(smartcarRestClient, properties, Clock.systemUTC());
    }

    /** Package-private so tests can inject a fixed/controllable clock to assert renewal at the
     * token expiry boundary deterministically (mirrors MockChargingAdapter's testability seam). */
    SmartcarClient(RestClient smartcarRestClient, SmartcarProperties properties, Clock clock) {
        this.restClient = smartcarRestClient;
        this.properties = properties;
        this.clock = clock;
    }

    /** Builds the Smartcar Connect URL. {@code response_type=none} is used throughout, since this
     * integration authenticates with an application-level token, not a per-user OAuth code. */
    public String buildConnectUrl(String state, String externalId) {
        return UriComponentsBuilder.fromUriString(properties.connectBaseUrl())
                .queryParam("application_id", properties.applicationId())
                .queryParam("response_type", "none")
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("mode", properties.mode())
                .queryParam("scope", encode(CONNECT_SCOPE))
                .queryParam("state", state)
                .queryParam("external_id", externalId)
                .build()
                .toUriString();
    }

    /**
     * Lists the vehicles in the Smartcar user's account. Results are filtered here by
     * {@code userId} so an unexpected server-side default (e.g. no filtering applied) cannot leak
     * another user's vehicle.
     */
    public List<SmartcarVehicleCandidate> listVehicles(String smartcarUserId) {
        try {
            SmartcarConnectionsEnvelope envelope = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/connections").queryParam("userId", smartcarUserId).build())
                    .headers(headers -> headers.setBearerAuth(accessToken()))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(SmartcarConnectionsEnvelope.class);
            if (envelope == null || envelope.data() == null) {
                return List.of();
            }
            return envelope.data().stream()
                    .filter(connection -> connection.attributes() != null
                            && connection.attributes().user() != null
                            && smartcarUserId.equals(connection.attributes().user().id())
                            && connection.relationships() != null
                            && connection.relationships().vehicle() != null
                            && connection.relationships().vehicle().data() != null)
                    .map(connection -> new SmartcarVehicleCandidate(
                            connection.id(),
                            connection.relationships().vehicle().data().id(),
                            connection.attributes().vehicle() != null ? connection.attributes().vehicle().make() : null,
                            connection.attributes().vehicle() != null ? connection.attributes().vehicle().model() : null,
                            connection.attributes().vehicle() != null ? connection.attributes().vehicle().year() : null))
                    .collect(Collectors.toList());
        } catch (SmartcarProviderException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new SmartcarProviderException("Failed to list Smartcar vehicles for user " + smartcarUserId, ex);
        }
    }

    /** Reads the four telemetry signals via four independent Get Signal calls (see class Javadoc). */
    public SmartcarTelemetry readTelemetry(String smartcarUserId, String smartcarVehicleId) {
        try {
            Double stateOfCharge = fetchSignal(smartcarUserId, smartcarVehicleId, SIGNAL_STATE_OF_CHARGE).numericValue();
            Double range = fetchSignal(smartcarUserId, smartcarVehicleId, SIGNAL_RANGE).numericValue();
            Boolean isCharging = fetchSignal(smartcarUserId, smartcarVehicleId, SIGNAL_IS_CHARGING).booleanValue();
            Boolean isPluggedIn = fetchSignal(smartcarUserId, smartcarVehicleId, SIGNAL_IS_PLUGGED_IN).booleanValue();
            return new SmartcarTelemetry(stateOfCharge, range, isPluggedIn, isCharging, OffsetDateTime.now());
        } catch (SmartcarProviderException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new SmartcarProviderException(
                    "Failed to read telemetry for Smartcar vehicle " + smartcarVehicleId, ex);
        }
    }

    private SmartcarSignalEnvelope fetchSignal(String smartcarUserId, String smartcarVehicleId, String signalCode) {
        SmartcarSignalEnvelope envelope = restClient.get()
                .uri("/vehicles/{vehicleId}/signals/{code}", smartcarVehicleId, signalCode)
                .headers(headers -> {
                    headers.setBearerAuth(accessToken());
                    headers.set(HEADER_SC_USER_ID, smartcarUserId);
                })
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(SmartcarSignalEnvelope.class);
        return envelope != null ? envelope : new SmartcarSignalEnvelope(null);
    }

    /**
     * Removes the connection on Smartcar's side. Best-effort: the caller (see
     * {@code VehicleConnectionService}) always deletes the local link regardless of the outcome, so
     * a WattPilot user is never stuck with a link they cannot remove because Smartcar is unreachable.
     */
    public void removeConnection(String connectionId) {
        restClient.delete()
                .uri("/connections/{connectionId}", connectionId)
                .headers(headers -> headers.setBearerAuth(accessToken()))
                .retrieve()
                .toBodilessEntity();
    }

    private synchronized String accessToken() {
        CachedToken current = cachedToken;
        if (current != null && current.expiresAt().isAfter(clock.instant().plus(TOKEN_RENEWAL_MARGIN))) {
            return current.accessToken();
        }
        SmartcarTokenResponse response = fetchAppAccessToken();
        Instant expiresAt = clock.instant().plusSeconds(response.expiresIn());
        this.cachedToken = new CachedToken(response.accessToken(), expiresAt);
        return response.accessToken();
    }

    private SmartcarTokenResponse fetchAppAccessToken() {
        RestClient.RequestBodySpec request = restClient.post()
                .uri(properties.authBaseUrl())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE);
        String body = "grant_type=client_credentials&client_id=%s&client_secret=%s".formatted(
                encode(properties.clientId()), encode(properties.clientSecret()));
        SmartcarTokenResponse response = request.body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new SmartcarProviderException(
                            "Smartcar token request failed with HTTP " + res.getStatusCode().value());
                })
                .body(SmartcarTokenResponse.class);
        if (response == null || response.accessToken() == null) {
            throw new SmartcarProviderException("Smartcar token response was empty");
        }
        return response;
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(Objects.requireNonNullElse(value, ""), java.nio.charset.StandardCharsets.UTF_8);
    }

    private record CachedToken(String accessToken, Instant expiresAt) {
    }
}
