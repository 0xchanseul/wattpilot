package com.wattpilot.feedback.service;

import com.wattpilot.feedback.entity.Feedback;
import com.wattpilot.feedback.repository.FeedbackRepository;
import com.wattpilot.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final UserService userService;

    public FeedbackService(FeedbackRepository feedbackRepository, UserService userService) {
        this.feedbackRepository = feedbackRepository;
        this.userService = userService;
    }

    @Transactional
    public void submit(Long userId, String message) {
        String email = userService.getById(userId).getEmail();
        feedbackRepository.save(new Feedback(email, message.trim()));
    }
}
