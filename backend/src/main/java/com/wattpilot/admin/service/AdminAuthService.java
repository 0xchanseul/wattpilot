package com.wattpilot.admin.service;

import com.wattpilot.admin.AdminProperties;
import com.wattpilot.admin.security.AdminTokenProvider;
import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;

/** Checks the admin password and issues the admin session token. */
@Service
public class AdminAuthService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);

    private final AdminProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final AdminTokenProvider tokenProvider;

    public AdminAuthService(AdminProperties properties, PasswordEncoder passwordEncoder,
                            AdminTokenProvider tokenProvider) {
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    /**
     * @throws BusinessException {@code INVALID_ADMIN_PASSWORD} for a wrong password, and also when no
     *                           admin password is configured, so a closed area looks like a wrong password
     */
    public String login(String password) {
        if (!properties.enabled() || !passwordMatches(password)) {
            log.warn("Admin login failed");
            throw new BusinessException(ErrorCode.INVALID_ADMIN_PASSWORD);
        }
        return tokenProvider.createToken();
    }

    public Duration sessionTtl() {
        return properties.sessionTtl();
    }

    private boolean passwordMatches(String password) {
        try {
            return passwordEncoder.matches(password, properties.encodedPasswordHash());
        } catch (IllegalArgumentException ex) {
            // A malformed hash in the configuration must read as a failed login, not as a server error.
            log.error("The configured admin password hash is not a valid hash");
            return false;
        }
    }
}
