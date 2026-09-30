package com.wattpilot.integration.smartcar.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;

/**
 * Response of {@code GET /v3/vehicles/{id}/signals/{code}}. Confirmed against
 * smartcar.com/docs/api-reference/get-signal on 2026-09-28:
 * <pre>{@code
 * {"data": {"id": "...", "attributes": {"code": "...", "status": {"value": "SUCCESS"},
 *                                        "body": {"unit": "...", "value": ...}}}}
 * }</pre>
 * {@code body.value} is typed as a raw {@link JsonNode} because its shape differs by signal
 * (number for a state-of-charge/range signal, boolean for a charge-status signal).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SmartcarSignalEnvelope(Data data) {

    private static final String STATUS_SUCCESS = "SUCCESS";

    /** The signal's numeric value, or {@code null} if unavailable or not numeric. */
    public Double numericValue() {
        JsonNode value = successfulBodyValue();
        return value != null && value.isNumber() ? value.asDouble() : null;
    }

    /** The signal's boolean value, or {@code null} if unavailable or not boolean. */
    public Boolean booleanValue() {
        JsonNode value = successfulBodyValue();
        return value != null && value.isBoolean() ? value.asBoolean() : null;
    }

    private JsonNode successfulBodyValue() {
        if (data == null || data.attributes() == null) {
            return null;
        }
        Attributes attributes = data.attributes();
        if (attributes.status() == null || !STATUS_SUCCESS.equals(attributes.status().value())) {
            return null;
        }
        return attributes.body() != null ? attributes.body().value() : null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(String id, Attributes attributes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Attributes(String code, Status status, Body body) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Status(String value) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(JsonNode value, String unit) {
    }
}
