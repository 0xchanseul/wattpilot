package com.wattpilot.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A single rejected field from a bean-validation failure.
 * Matches the {@code FieldError} schema in docs/openapi.yaml.
 *
 * <p>{@code rejectedValue} is optional in the contract: it is omitted for sensitive fields
 * so an error response never echoes a submitted credential back to the client.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldErrorDetail(String field, Object rejectedValue, String reason) {
}
