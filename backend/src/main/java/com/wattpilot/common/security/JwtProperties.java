package com.wattpilot.common.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Signing and lifetime settings for the tokens issued by the auth module.
 *
 * @param secret               Base64-encoded HS256 key; must decode to at least 32 bytes
 * @param issuer               value written to, and required in, the {@code iss} claim
 * @param accessTokenTtl       lifetime of a JWT access token
 * @param sessionTtl           absolute lifetime of a login session without "keep me signed in";
 *                             measured from the initial login and never extended by a rotation
 * @param rememberMeSessionTtl absolute session lifetime when the user ticked "keep me signed in"
 */
@Validated
@ConfigurationProperties(prefix = "wattpilot.security.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotBlank String issuer,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration sessionTtl,
        @NotNull Duration rememberMeSessionTtl
) {

    /** Absolute session lifetime to grant a fresh login, chosen by the "keep me signed in" choice. */
    public Duration sessionTtlFor(boolean rememberMe) {
        return rememberMe ? rememberMeSessionTtl : sessionTtl;
    }
}
