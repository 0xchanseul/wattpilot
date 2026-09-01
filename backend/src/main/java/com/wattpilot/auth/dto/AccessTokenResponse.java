package com.wattpilot.auth.dto;

/**
 * The access-token half of a freshly issued token pair, returned in the response body. Matches the
 * {@code AccessTokenResponse} schema in docs/openapi.yaml.
 *
 * <p>The refresh token is delivered separately as an {@code HttpOnly} {@code Set-Cookie} header and
 * never appears in a response body.
 *
 * @param expiresIn lifetime of the access token in seconds; the refresh token outlives it
 */
public record AccessTokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}
