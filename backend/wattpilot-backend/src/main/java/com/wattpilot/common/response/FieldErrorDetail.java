package com.wattpilot.common.response;

/**
 * A single rejected field from a bean-validation failure.
 * Matches the {@code FieldError} schema in docs/openapi.yaml.
 */
public record FieldErrorDetail(String field, Object rejectedValue, String reason) {
}
