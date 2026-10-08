package com.wattpilot.feedback.service;

import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import com.wattpilot.feedback.entity.Feedback;
import com.wattpilot.feedback.repository.FeedbackRepository;
import com.wattpilot.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

@Service
public class FeedbackService {

    static final int MAX_MESSAGES_PER_WINDOW = 5;
    static final Duration WINDOW = Duration.ofHours(24);

    private final FeedbackRepository feedbackRepository;
    private final UserService userService;
    private final Clock clock;

    public FeedbackService(FeedbackRepository feedbackRepository, UserService userService, Clock clock) {
        this.feedbackRepository = feedbackRepository;
        this.userService = userService;
        this.clock = clock;
    }

    @Transactional
    public void submit(Long userId, String message) {
        String email = userService.getById(userId).getEmail();
        // The count is not atomic with the insert, so a burst can overshoot by a few messages; the
        // limit only bounds storage growth and does not need to be exact.
        OffsetDateTime since = OffsetDateTime.now(clock).minus(WINDOW);
        if (feedbackRepository.countByEmailAndCreatedAtAfter(email, since) >= MAX_MESSAGES_PER_WINDOW) {
            throw new BusinessException(ErrorCode.FEEDBACK_LIMIT_REACHED);
        }
        feedbackRepository.save(new Feedback(email, message.trim()));
    }
}
