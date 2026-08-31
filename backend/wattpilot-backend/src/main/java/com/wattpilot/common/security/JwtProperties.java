package com.wattpilot.common.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Signing and lifetime settings for the tokens issued by the auth module.
 *
 * @param secret          Base64-encoded HS256 key; must decode to at least 32 bytes
 * @param issuer          value written to, and required in, the {@code iss} claim
 * @param accessTokenTtl  lifetime of a JWT access token
 * @param refreshTokenTtl lifetime of an opaque refresh token
 */
@Validated
@ConfigurationProperties(prefix = "wattpilot.security.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotBlank String issuer,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl
) {
}
