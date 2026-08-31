package com.wattpilot.user.controller;

import com.wattpilot.common.security.AuthenticatedUser;
import com.wattpilot.user.dto.UserResponse;
import com.wattpilot.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Auth", description = "Sign-up, login, token refresh, and current user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Get current user")
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return ResponseEntity.ok(UserResponse.from(userService.getById(authenticatedUser.userId())));
    }
}
