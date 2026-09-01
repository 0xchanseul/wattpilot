package com.wattpilot.auth.dto;

import com.wattpilot.user.dto.UserResponse;

/**
 * Sign-up and login response. Matches the {@code AuthResponse} schema in docs/openapi.yaml, which
 * composes {@code AccessTokenResponse} with a {@code user} object; the access-token fields are
 * repeated here so the JSON stays flat rather than nesting them under their own key.
 *
 * <p>The refresh token is not part of this body: it is set as an {@code HttpOnly} cookie on the
 * same response.
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {

    public static AuthResponse of(AccessTokenResponse tokens, UserResponse user) {
        return new AuthResponse(tokens.accessToken(), tokens.tokenType(), tokens.expiresIn(), user);
    }
}
