package com.wattpilot.feedback.service;

import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import com.wattpilot.feedback.entity.Feedback;
import com.wattpilot.feedback.repository.FeedbackRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class FeedbackService {

    static final int MAX_MESSAGES_PER_WINDOW = 5;
    static final Duration WINDOW = Duration.ofHours(24);

    private final FeedbackRepository feedbackRepository;
    private final FeedbackRateLimiter rateLimiter;

    FeedbackService(FeedbackRepository feedbackRepository, FeedbackRateLimiter rateLimiter) {
        this.feedbackRepository = feedbackRepository;
        this.rateLimiter = rateLimiter;
    }

    /** Stores the message anonymously; the user id is only used to apply the send limit. */
    public void submit(Long userId, String message) {
        if (!rateLimiter.tryAcquire(userId)) {
            throw new BusinessException(ErrorCode.FEEDBACK_LIMIT_REACHED);
        }
        feedbackRepository.save(new Feedback(message.trim()));
    }
}
