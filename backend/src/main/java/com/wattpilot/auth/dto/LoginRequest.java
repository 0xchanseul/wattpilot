package com.wattpilot.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Matches the {@code LoginRequest} schema in docs/openapi.yaml.
 *
 * <p>No length or format rules beyond presence: rejecting a credential for violating the current
 * sign-up policy would both leak that policy and lock out accounts created under an earlier one.
 *
 * <p>{@code rememberMe} is a boxed {@code Boolean} so an absent field deserializes to {@code null}
 * rather than failing the whole request; the compact constructor then normalizes it to
 * {@code false}. When {@code true}, the session gets the longer "keep me signed in" absolute
 * lifetime.
 */
public record LoginRequest(
        @NotBlank String email,
        @NotBlank String password,
        Boolean rememberMe
) {

    public LoginRequest {
        rememberMe = Boolean.TRUE.equals(rememberMe);
    }
}
