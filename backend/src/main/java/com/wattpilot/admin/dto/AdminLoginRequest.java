package com.wattpilot.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminLoginRequest(@NotBlank @Size(max = 128) String password) {
}
