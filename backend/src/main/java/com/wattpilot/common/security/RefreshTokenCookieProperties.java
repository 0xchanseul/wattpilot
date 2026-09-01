package com.wattpilot.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Attributes of the {@code HttpOnly} cookie that carries the opaque refresh token.
 *
 * <p>Delivering the refresh token as an {@code HttpOnly} cookie keeps it out of reach of browser
 * JavaScript, so an XSS payload cannot read it the way it could a value in {@code localStorage}.
 * {@code SameSite=Lax} together with the narrow {@link #COOKIE_PATH} stops a cross-site page from
 * driving the cookie-authenticated {@code POST /auth/refresh} endpoint, which is why V1 needs no
 * separate CSRF token.
 *
 * @param secure   whether to set the {@code Secure} attribute; browsers still accept a {@code Secure}
 *                 cookie over {@code http://localhost}, so this can stay {@code true} in local
 *                 development. Only set it to {@code false} when the API is reached over plain HTTP
 *                 on a non-localhost host.
 * @param sameSite {@code SameSite} attribute value: {@code Lax}, {@code Strict} or {@code None}.
 *                 {@code None} requires {@code secure=true} and removes the SameSite CSRF defence, so
 *                 it is only appropriate when the frontend and API are served from unrelated sites.
 * @param domain   optional {@code Domain} attribute; left unset the cookie is host-only.
 */
@ConfigurationProperties(prefix = "wattpilot.security.refresh-cookie")
public record RefreshTokenCookieProperties(
        @DefaultValue("true") boolean secure,
        @DefaultValue("Lax") String sameSite,
        String domain
) {

    /** Cookie name, fixed by the API contract in docs/openapi.yaml. */
    public static final String COOKIE_NAME = "wp_refresh_token";

    /** Scoped to the auth endpoints that read the cookie so it is not sent with any other request. */
    public static final String COOKIE_PATH = "/api/v1/auth";
}
