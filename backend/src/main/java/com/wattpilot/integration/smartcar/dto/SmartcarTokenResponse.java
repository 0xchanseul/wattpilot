package com.wattpilot.integration.smartcar.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response of {@code POST https://iam.smartcar.com/oauth2/token} (client_credentials). Confirmed
 * against smartcar.com/docs/api-reference/authorization/request-access-token on 2026-09-28.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SmartcarTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn
) {
}
