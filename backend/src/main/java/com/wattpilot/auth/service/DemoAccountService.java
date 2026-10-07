package com.wattpilot.auth.service;

import com.wattpilot.auth.DemoProperties;
import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import com.wattpilot.ev.service.DemoEvService;
import com.wattpilot.user.entity.User;
import com.wattpilot.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * Creates the temporary visitor accounts behind the demo login.
 *
 * <p>A visitor gets an account of their own, so their charging schedules never collide with another
 * visitor's. Only the EVs and their Smartcar connections are shared, by copy, with the template.
 */
@Service
public class DemoAccountService {

    private static final Logger log = LoggerFactory.getLogger(DemoAccountService.class);

    private final DemoProperties properties;
    private final UserService userService;
    private final DemoEvService demoEvService;

    public DemoAccountService(DemoProperties properties, UserService userService, DemoEvService demoEvService) {
        this.properties = properties;
        this.userService = userService;
        this.demoEvService = demoEvService;
    }

    public Duration accountLifetime() {
        return properties.ttl();
    }

    /**
     * The capacity check is not atomic with the insert, so a burst of simultaneous requests can
     * overshoot the limit by a few accounts. That is acceptable: it exists to bound growth, not to
     * enforce an exact quota.
     */
    @Transactional
    public User createAccount() {
        if (!properties.enabled()) {
            throw new BusinessException(ErrorCode.DEMO_UNAVAILABLE);
        }
        User template = userService.findByEmail(properties.templateEmail()).orElse(null);
        if (template == null) {
            log.warn("Demo login is enabled but the template account does not exist");
            throw new BusinessException(ErrorCode.DEMO_UNAVAILABLE);
        }
        if (userService.countDemoAccounts() >= properties.maxActiveAccounts()) {
            throw new BusinessException(ErrorCode.DEMO_CAPACITY_REACHED);
        }

        User visitor = userService.registerDemo(template.getDefaultPriceArea());
        if (demoEvService.copyDemoEvs(template.getId(), visitor.getId()) == 0) {
            // A demo account with nothing to show is a misconfiguration, so it is not handed out;
            // the exception rolls back the visitor created above.
            log.warn("Demo login is enabled but the template account has no active EV");
            throw new BusinessException(ErrorCode.DEMO_UNAVAILABLE);
        }
        return visitor;
    }
}
