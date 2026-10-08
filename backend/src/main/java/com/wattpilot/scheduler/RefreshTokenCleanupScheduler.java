package com.wattpilot.scheduler;

import com.wattpilot.auth.service.RefreshTokenCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the daily cleanup of refresh tokens whose session has ended. */
@Component
public class RefreshTokenCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupScheduler.class);

    private final RefreshTokenCleanupService cleanupService;

    public RefreshTokenCleanupScheduler(RefreshTokenCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "Europe/Oslo")
    public void run() {
        int deleted = cleanupService.deleteExpiredTokens();
        // Only speak up on a run that had work to do.
        if (deleted > 0) {
            log.info("Deleted {} expired refresh token(s)", deleted);
        }
    }
}
