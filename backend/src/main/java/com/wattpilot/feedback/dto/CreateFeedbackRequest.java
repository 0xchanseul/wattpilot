package com.wattpilot.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Matches the {@code CreateFeedbackRequest} schema in docs/openapi.yaml. */
public record CreateFeedbackRequest(
        @NotBlank @Size(max = 2000) String message
) {
}
