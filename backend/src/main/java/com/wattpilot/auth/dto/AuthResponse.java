package com.wattpilot.auth.dto;

import com.wattpilot.user.dto.UserResponse;

/**
 * Sign-up and login response. Matches the {@code AuthResponse} schema in docs/openapi.yaml, which
 * composes {@code TokenResponse} with a {@code user} object; the token fields are repeated here so
 * the JSON stays flat rather than nesting the token pair under its own key.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {

    public static AuthResponse of(TokenResponse tokens, UserResponse user) {
        return new AuthResponse(
                tokens.accessToken(),
                tokens.refreshToken(),
                tokens.tokenType(),
                tokens.expiresIn(),
                user);
    }
}
