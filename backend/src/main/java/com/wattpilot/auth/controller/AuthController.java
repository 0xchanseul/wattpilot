package com.wattpilot.auth.controller;

import com.wattpilot.auth.dto.AuthResponse;
import com.wattpilot.auth.dto.LoginRequest;
import com.wattpilot.auth.dto.RefreshTokenRequest;
import com.wattpilot.auth.dto.SignUpRequest;
import com.wattpilot.auth.dto.TokenResponse;
import com.wattpilot.auth.service.AuthService;
import com.wattpilot.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Sign-up, login, token refresh, and current user")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Create an account")
    @SecurityRequirements
    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signUp(@Valid @RequestBody SignUpRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signUp(request));
    }

    @Operation(summary = "Log in")
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Public because the refresh token in the body is itself the credential; requiring a valid
     * access token as well would defeat the purpose of refreshing an expired one.
     */
    @Operation(summary = "Refresh access token")
    @SecurityRequirements
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @Operation(summary = "Log out and revoke refresh token")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
                                       @Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(authenticatedUser.userId(), request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
