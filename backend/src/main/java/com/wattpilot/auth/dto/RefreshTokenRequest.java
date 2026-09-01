package com.wattpilot.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Matches the {@code RefreshTokenRequest} schema in docs/openapi.yaml. Used by both refresh and
 * logout, which act on the same token.
 */
public record RefreshTokenRequest(@NotBlank String refreshToken) {
}
