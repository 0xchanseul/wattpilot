package com.wattpilot.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Matches the {@code LoginRequest} schema in docs/openapi.yaml.
 *
 * <p>No length or format rules beyond presence: rejecting a credential for violating the current
 * sign-up policy would both leak that policy and lock out accounts created under an earlier one.
 */
public record LoginRequest(
        @NotBlank String email,
        @NotBlank String password
) {
}
