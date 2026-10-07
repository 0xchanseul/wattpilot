package com.wattpilot.auth.service;

import com.wattpilot.auth.DemoProperties;
import com.wattpilot.charging.service.ChargingScheduleService;
import com.wattpilot.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Deletes demo accounts that have outlived {@link DemoProperties#ttl()}.
 *
 * <p>Deliberately not transactional as a whole: each account is deleted in its own transaction, so
 * one failure never undoes or blocks the rest of the batch. Only the database rows are removed. The
 * vehicle connection copies are never reported to Smartcar, because they share one Smartcar
 * connection with the template account and the other visitors.
 */
@Service
public class DemoAccountCleanupService {

    private static final Logger log = LoggerFactory.getLogger(DemoAccountCleanupService.class);

    private final DemoProperties properties;
    private final UserService userService;
    private final ChargingScheduleService chargingScheduleService;
    private final Clock clock;

    public DemoAccountCleanupService(DemoProperties properties, UserService userService,
                                     ChargingScheduleService chargingScheduleService, Clock clock) {
        this.properties = properties;
        this.userService = userService;
        this.chargingScheduleService = chargingScheduleService;
        this.clock = clock;
    }

    /** @return the number of accounts deleted */
    public int deleteExpiredAccounts() {
        OffsetDateTime cutoff = OffsetDateTime.now(clock).minus(properties.ttl());
        List<Long> expiredIds = userService.findExpiredDemoUserIds(cutoff, properties.cleanupBatchSize());

        int deleted = 0;
        for (Long userId : expiredIds) {
            try {
                // A running charge is left alone so the execution scheduler is not cut off mid-session;
                // the account is picked up by a later run once the charge has ended.
                if (!chargingScheduleService.hasInProgressSchedule(userId) && userService.deleteDemoAccount(userId)) {
                    deleted++;
                }
            } catch (Exception e) {
                log.error("Failed to delete expired demo account id={}", userId, e);
            }
        }
        return deleted;
    }
}
