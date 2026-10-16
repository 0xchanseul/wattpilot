package com.wattpilot.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Matches the {@code DeleteAccountRequest} schema in docs/openapi.yaml.
 *
 * <p>Presence only, like {@link LoginRequest}: the password is checked against the stored hash, not
 * against the current sign-up policy.
 */
public record DeleteAccountRequest(
        @NotBlank String password
) {
}
