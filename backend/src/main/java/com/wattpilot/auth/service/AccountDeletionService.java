package com.wattpilot.auth.service;

import com.wattpilot.auth.DemoProperties;
import com.wattpilot.charging.service.ChargingScheduleService;
import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import com.wattpilot.ev.service.VehicleConnectionService;
import com.wattpilot.user.entity.User;
import com.wattpilot.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Lets a regular user delete their own account, with everything it owns, immediately.
 *
 * <p>Deliberately not transactional as a whole: the Smartcar connections are removed in their own
 * transaction before the account row goes, so a slow or failing Smartcar call never holds the
 * account transaction open. If the final delete fails, the user keeps a working account without its
 * vehicle links and can simply retry.
 *
 * <p>A charge starting between the in-progress check and the delete is not guarded against; it
 * would be removed together with the account, which is what the user asked for. Demo visitor
 * accounts and the shared demo template account cannot be deleted here.
 */
@Service
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    private final UserService userService;
    private final ChargingScheduleService chargingScheduleService;
    private final VehicleConnectionService vehicleConnectionService;
    private final PasswordEncoder passwordEncoder;
    private final DemoProperties demoProperties;

    public AccountDeletionService(UserService userService, ChargingScheduleService chargingScheduleService,
                                  VehicleConnectionService vehicleConnectionService,
                                  PasswordEncoder passwordEncoder, DemoProperties demoProperties) {
        this.userService = userService;
        this.chargingScheduleService = chargingScheduleService;
        this.vehicleConnectionService = vehicleConnectionService;
        this.passwordEncoder = passwordEncoder;
        this.demoProperties = demoProperties;
    }

    public void deleteAccount(Long userId, String rawPassword) {
        User user = userService.getById(userId);
        if (user.isDemo() || isDemoTemplate(user)) {
            throw new BusinessException(ErrorCode.ACCOUNT_DELETION_NOT_ALLOWED);
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INCORRECT_PASSWORD);
        }
        if (chargingScheduleService.hasInProgressSchedule(userId)) {
            throw new BusinessException(ErrorCode.ACCOUNT_CHARGING_IN_PROGRESS);
        }

        vehicleConnectionService.removeAllForUser(userId);
        userService.deleteRegularAccount(userId);
        log.info("Account deleted by its owner: userId={}", userId);
    }

    private boolean isDemoTemplate(User user) {
        String templateEmail = demoProperties.templateEmail();
        return StringUtils.hasText(templateEmail) && user.getEmail().equalsIgnoreCase(templateEmail.trim());
    }
}
