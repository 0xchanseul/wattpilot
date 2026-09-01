package com.wattpilot.auth.controller;

import com.wattpilot.auth.dto.AccessTokenResponse;
import com.wattpilot.auth.dto.AuthResponse;
import com.wattpilot.auth.dto.LoginRequest;
import com.wattpilot.auth.dto.SignUpRequest;
import com.wattpilot.auth.service.AuthService;
import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import com.wattpilot.common.security.AuthenticatedUser;
import com.wattpilot.common.security.RefreshTokenCookieProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Sign-up, login, token refresh, and current user")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieProperties cookieProperties;

    public AuthController(AuthService authService, RefreshTokenCookieProperties cookieProperties) {
        this.authService = authService;
        this.cookieProperties = cookieProperties;
    }

    @Operation(summary = "Create an account")
    @SecurityRequirements
    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signUp(@Valid @RequestBody SignUpRequest request) {
        AuthService.AuthResult result = authService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie(result.refreshToken(), result.refreshTokenValidity()))
                .body(result.body());
    }

    @Operation(summary = "Log in")
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthService.AuthResult result = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie(result.refreshToken(), result.refreshTokenValidity()))
                .body(result.body());
    }

    /**
     * Public because the refresh-token cookie is itself the credential; requiring a valid access
     * token as well would defeat the purpose of refreshing an expired one.
     */
    @Operation(summary = "Refresh access token")
    @SecurityRequirement(name = "refreshCookie")
    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(
            @CookieValue(name = RefreshTokenCookieProperties.COOKIE_NAME, required = false) String refreshToken) {
        AuthService.RefreshResult result = authService.refresh(requirePresent(refreshToken));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie(result.refreshToken(), result.refreshTokenValidity()))
                .body(result.body());
    }

    /**
     * Requires an access token so one account cannot revoke another's session, and always clears
     * the cookie so the browser is left logged out even when the token was already gone.
     */
    @Operation(summary = "Log out and revoke refresh token",
            description = "Requires the access token; also reads and clears the wp_refresh_token cookie.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
                                       @CookieValue(name = RefreshTokenCookieProperties.COOKIE_NAME, required = false)
                                       String refreshToken) {
        if (StringUtils.hasText(refreshToken)) {
            authService.logout(authenticatedUser.userId(), refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearedRefreshTokenCookie())
                .build();
    }

    private static String requirePresent(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        return refreshToken;
    }

    private String refreshTokenCookie(String value, Duration maxAge) {
        return baseCookie(value).maxAge(maxAge).build().toString();
    }

    private String clearedRefreshTokenCookie() {
        return baseCookie("").maxAge(0).build().toString();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(RefreshTokenCookieProperties.COOKIE_NAME, value)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .path(RefreshTokenCookieProperties.COOKIE_PATH)
                .sameSite(cookieProperties.sameSite());
        if (StringUtils.hasText(cookieProperties.domain())) {
            builder.domain(cookieProperties.domain());
        }
        return builder;
    }
}
