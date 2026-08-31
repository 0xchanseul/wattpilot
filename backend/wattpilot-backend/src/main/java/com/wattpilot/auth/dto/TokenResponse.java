package com.wattpilot.auth.dto;

/**
 * A freshly issued token pair. Matches the {@code TokenResponse} schema in docs/openapi.yaml.
 *
 * @param expiresIn lifetime of the access token in seconds; the refresh token outlives it
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
}
