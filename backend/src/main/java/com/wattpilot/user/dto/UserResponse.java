package com.wattpilot.user.dto;

import com.wattpilot.common.PriceArea;
import com.wattpilot.user.entity.User;
import com.wattpilot.user.entity.UserStatus;

import java.time.OffsetDateTime;

/**
 * Public view of an account. Matches the {@code User} schema in docs/openapi.yaml and
 * deliberately omits the password hash.
 */
public record UserResponse(
        Long id,
        String email,
        String name,
        PriceArea defaultPriceArea,
        UserStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getDefaultPriceArea(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
