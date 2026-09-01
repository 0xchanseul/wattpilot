package com.wattpilot.auth.dto;

import com.wattpilot.common.PriceArea;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Matches the {@code SignUpRequest} schema in docs/openapi.yaml.
 *
 * <p>The password length is the only rule applied: 8 is the documented minimum, and 72 bytes is
 * the point at which BCrypt silently ignores the remaining input.
 */
public record SignUpRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 100) String name,
        @NotNull PriceArea defaultPriceArea
) {
}
