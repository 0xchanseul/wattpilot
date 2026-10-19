package com.wattpilot.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Settings for the operator-only statistics area.
 *
 * <p>There is one operator and no admin accounts: access is granted by a single password whose bcrypt
 * hash is supplied by the environment. Without a hash the area is closed to everyone.
 *
 * @param passwordHash bcrypt hash of the admin password, with or without the {@code {bcrypt}} prefix;
 *                     empty disables admin login
 * @param sessionTtl   lifetime of an admin login; there is no refresh, so it ends after this long
 */
@ConfigurationProperties("wattpilot.admin")
public record AdminProperties(
        String passwordHash,
        @DefaultValue("4h") Duration sessionTtl
) {

    public AdminProperties {
        if (sessionTtl.isZero() || sessionTtl.isNegative()) {
            throw new IllegalArgumentException("wattpilot.admin.session-ttl must be positive");
        }
    }

    public boolean enabled() {
        return StringUtils.hasText(passwordHash);
    }

    /** The stored hash in the delegating encoder's format, so a bare {@code $2a$...} value also works. */
    public String encodedPasswordHash() {
        String hash = passwordHash.trim();
        return hash.startsWith("{") ? hash : "{bcrypt}" + hash;
    }
}
