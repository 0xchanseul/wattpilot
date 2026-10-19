package com.wattpilot.admin.controller;

import com.wattpilot.admin.dto.AdminLoginRequest;
import com.wattpilot.admin.security.AdminTokenProvider;
import com.wattpilot.admin.service.AdminAuthService;
import com.wattpilot.common.security.RefreshTokenCookieProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "Operator-only statistics")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final RefreshTokenCookieProperties cookieProperties;

    public AdminAuthController(AdminAuthService adminAuthService, RefreshTokenCookieProperties cookieProperties) {
        this.adminAuthService = adminAuthService;
        this.cookieProperties = cookieProperties;
    }

    @Operation(summary = "Log in as the operator",
            description = "Sets the HttpOnly wp_admin_session cookie. There is no token in the response body.")
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<Void> login(@Valid @RequestBody AdminLoginRequest request) {
        String token = adminAuthService.login(request.password());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie(token, adminAuthService.sessionTtl()))
                .build();
    }

    /** Public on purpose: clearing a cookie needs no proof, and an expired session must still be able to log out. */
    @Operation(summary = "Log out of the admin area")
    @SecurityRequirements
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO))
                .build();
    }

    @Operation(summary = "Check the admin login", description = "204 while the admin cookie is valid, otherwise 401.")
    @GetMapping("/session")
    public ResponseEntity<Void> session() {
        return ResponseEntity.noContent().build();
    }

    // Strict plus a path limited to the admin API keeps the cookie off every other request, and the
    // admin API only reads, so no CSRF token is needed.
    private String cookie(String value, Duration maxAge) {
        return ResponseCookie.from(AdminTokenProvider.COOKIE_NAME, value)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .path(AdminTokenProvider.COOKIE_PATH)
                .sameSite("Strict")
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
