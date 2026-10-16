package com.wattpilot.feedback.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class FeedbackRateLimiterTest {

    private static final Instant START = Instant.parse("2026-01-15T12:00:00Z");

    private final AtomicReference<Instant> now = new AtomicReference<>(START);
    private final Clock clock = new Clock() {
        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };

    private final FeedbackRateLimiter limiter = new FeedbackRateLimiter(clock, 3, Duration.ofHours(24));

    @Test
    void allowsUpToTheLimitAndThenRejects() {
        assertThat(limiter.tryAcquire(1L)).isTrue();
        assertThat(limiter.tryAcquire(1L)).isTrue();
        assertThat(limiter.tryAcquire(1L)).isTrue();
        assertThat(limiter.tryAcquire(1L)).isFalse();
    }

    @Test
    void countsEachUserSeparately() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire(1L);
        }

        assertThat(limiter.tryAcquire(1L)).isFalse();
        assertThat(limiter.tryAcquire(2L)).isTrue();
    }

    @Test
    void allowsAgainOnceTheOldestSendLeavesTheWindow() {
        limiter.tryAcquire(1L);
        now.set(START.plus(Duration.ofHours(1)));
        limiter.tryAcquire(1L);
        limiter.tryAcquire(1L);
        assertThat(limiter.tryAcquire(1L)).isFalse();

        // The first send is exactly 24 hours old: it no longer counts, the other two still do.
        now.set(START.plus(Duration.ofHours(24)));
        assertThat(limiter.tryAcquire(1L)).isTrue();
        assertThat(limiter.tryAcquire(1L)).isFalse();
    }

    @Test
    void aRejectedAttemptDoesNotExtendTheBlock() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire(1L);
        }
        now.set(START.plus(Duration.ofHours(23)));
        assertThat(limiter.tryAcquire(1L)).isFalse();

        now.set(START.plus(Duration.ofHours(24)));
        assertThat(limiter.tryAcquire(1L)).isTrue();
    }
}
