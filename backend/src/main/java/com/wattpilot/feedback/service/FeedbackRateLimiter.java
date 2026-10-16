package com.wattpilot.feedback.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limits how many feedback messages one account may send in a rolling window.
 *
 * <p>Feedback rows are anonymous, so the sender cannot be counted in the database. The counts live
 * in memory instead and are lost on a restart, which is acceptable on a single instance: the limit
 * only bounds storage growth and does not need to be exact. Only send times are kept, keyed by user
 * id, and an entry disappears as soon as all of its sends have left the window.
 */
@Component
class FeedbackRateLimiter {

    private final Clock clock;
    private final int maxPerWindow;
    private final Duration window;
    private final ConcurrentHashMap<Long, Deque<Instant>> sendsByUser = new ConcurrentHashMap<>();

    @Autowired
    FeedbackRateLimiter(Clock clock) {
        this(clock, FeedbackService.MAX_MESSAGES_PER_WINDOW, FeedbackService.WINDOW);
    }

    FeedbackRateLimiter(Clock clock, int maxPerWindow, Duration window) {
        this.clock = clock;
        this.maxPerWindow = maxPerWindow;
        this.window = window;
    }

    /** Records a send for the user and returns true, or returns false when the limit is reached. */
    boolean tryAcquire(Long userId) {
        Instant now = clock.instant();
        Instant windowStart = now.minus(window);
        boolean[] allowed = new boolean[1];
        sendsByUser.compute(userId, (id, sends) -> {
            Deque<Instant> recent = sends != null ? sends : new ArrayDeque<>();
            while (!recent.isEmpty() && !recent.peekFirst().isAfter(windowStart)) {
                recent.pollFirst();
            }
            if (recent.size() < maxPerWindow) {
                recent.addLast(now);
                allowed[0] = true;
            }
            return recent.isEmpty() ? null : recent;
        });
        return allowed[0];
    }
}
