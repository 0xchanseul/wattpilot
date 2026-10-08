package com.wattpilot.auth.service;

import com.wattpilot.auth.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Removes refresh tokens of sessions that ended. Nothing else deletes them, so without this the
 * table grows with every login for as long as the service runs.
 */
@Service
public class RefreshTokenCleanupService {

    /** Kept briefly past expiry so a client with a slightly skewed clock still gets "expired", not "unknown". */
    static final Duration RETENTION_AFTER_EXPIRY = Duration.ofDays(1);

    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    public RefreshTokenCleanupService(RefreshTokenRepository refreshTokenRepository, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
    }

    /** @return the number of deleted tokens */
    @Transactional
    public int deleteExpiredTokens() {
        return refreshTokenRepository.deleteExpiredBefore(OffsetDateTime.now(clock).minus(RETENTION_AFTER_EXPIRY));
    }
}
