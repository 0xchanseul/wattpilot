package com.wattpilot.scheduler;

import com.wattpilot.auth.service.DemoAccountCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fires the hourly cleanup of expired demo accounts. Only present where the demo login is enabled,
 * since nothing creates demo accounts elsewhere.
 */
@Component
@ConditionalOnProperty(prefix = "wattpilot.demo", name = "enabled", havingValue = "true")
public class DemoAccountCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(DemoAccountCleanupScheduler.class);

    private final DemoAccountCleanupService cleanupService;

    public DemoAccountCleanupScheduler(DemoAccountCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(cron = "${wattpilot.demo.cleanup-cron:0 0 * * * *}")
    public void run() {
        int deleted = cleanupService.deleteExpiredAccounts();
        // Only speak up on a run that had work to do; an idle hour stays silent.
        if (deleted > 0) {
            log.info("Deleted {} expired demo account(s)", deleted);
        }
    }
}
