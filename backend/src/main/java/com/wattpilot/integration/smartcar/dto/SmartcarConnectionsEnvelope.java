package com.wattpilot.integration.smartcar.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Response of {@code GET /v3/connections}. Confirmed live against a real Smartcar simulated
 * connection on 2026-09-29:
 * <pre>{@code
 * {"data": [{"id": "<connectionId>", "attributes": {"vehicle": {"make", "model", "year"},
 *                                                    "user": {"id": "<smartcarUserId>"}},
 *            "relationships": {"vehicle": {"data": {"id": "<vehicleId>"}}}}]}
 * }</pre>
 * The vehicle id lives under {@code relationships}, not {@code attributes}, unlike the earlier
 * assumption recorded in TODO.md §3.3. {@link com.wattpilot.integration.smartcar.SmartcarClient#listVehicles}
 * also filters the result client-side by {@code userId}, so a wrong/ignored server-side filter
 * cannot leak another Smartcar user's vehicles.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SmartcarConnectionsEnvelope(List<Connection> data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Connection(String id, Attributes attributes, Relationships relationships) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Attributes(User user, Vehicle vehicle) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record User(String id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Vehicle(String make, String model, Integer year) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Relationships(RelationshipRef vehicle) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RelationshipRef(RefData data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RefData(String id) {
    }
}
