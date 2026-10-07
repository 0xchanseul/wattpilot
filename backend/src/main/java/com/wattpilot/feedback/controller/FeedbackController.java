package com.wattpilot.feedback.controller;

import com.wattpilot.common.security.AuthenticatedUser;
import com.wattpilot.feedback.dto.CreateFeedbackRequest;
import com.wattpilot.feedback.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/feedback")
@Tag(name = "Feedback", description = "Visitor feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @Operation(summary = "Send feedback")
    @PostMapping
    public ResponseEntity<Void> sendFeedback(@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
                                             @Valid @RequestBody CreateFeedbackRequest request) {
        feedbackService.submit(authenticatedUser.userId(), request.message());
        return ResponseEntity.noContent().build();
    }
}
