package com.wattpilot.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Configuration for the demo login, which gives each visitor a temporary account pre-loaded with
 * copies of a template demo account's EVs.
 *
 * <p>Off by default in every profile; the template account is required only when {@code enabled} is
 * true, so a deployment without a demo needs no extra setup.
 *
 * @param enabled           turns the demo login on; when false, it responds with
 *                          {@code DEMO_UNAVAILABLE} (503)
 * @param templateEmail     email of the existing account whose active EVs (and their Smartcar
 *                          connections) are copied for each visitor
 * @param ttl               lifetime of a visitor account; a demo session never outlives it
 * @param maxActiveAccounts upper bound on demo accounts existing at once, beyond which new demo
 *                          logins are refused with {@code DEMO_CAPACITY_REACHED} (429)
 * @param cleanupBatchSize  maximum expired demo accounts deleted per cleanup run; any beyond it wait
 *                          for the next run
 */
@ConfigurationProperties("wattpilot.demo")
public record DemoProperties(
        @DefaultValue("false") boolean enabled,
        String templateEmail,
        @DefaultValue("24h") Duration ttl,
        @DefaultValue("200") int maxActiveAccounts,
        @DefaultValue("200") int cleanupBatchSize
) {
    public DemoProperties {
        if (enabled && (templateEmail == null || templateEmail.isBlank())) {
            throw new IllegalArgumentException(
                    "wattpilot.demo.template-email must be set when wattpilot.demo.enabled=true");
        }
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("wattpilot.demo.ttl must be positive");
        }
        if (maxActiveAccounts < 1) {
            throw new IllegalArgumentException("wattpilot.demo.max-active-accounts must be at least 1");
        }
        if (cleanupBatchSize < 1) {
            throw new IllegalArgumentException("wattpilot.demo.cleanup-batch-size must be at least 1");
        }
    }
}
