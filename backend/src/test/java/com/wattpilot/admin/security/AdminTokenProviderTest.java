package com.wattpilot.admin.security;

import com.wattpilot.admin.AdminProperties;
import com.wattpilot.common.security.JwtProperties;
import com.wattpilot.common.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminTokenProviderTest {

    private static final JwtProperties JWT = new JwtProperties(
            Base64.getEncoder().encodeToString(new byte[32]), "wattpilot",
            Duration.ofMinutes(30), Duration.ofDays(7), Duration.ofDays(30));

    private static AdminTokenProvider providerWith(String passwordHash, Duration ttl) {
        return new AdminTokenProvider(JWT, new AdminProperties(passwordHash, ttl));
    }

    @Test
    void acceptsATokenItIssued() {
        AdminTokenProvider provider = providerWith("$2a$10$abc", Duration.ofHours(4));

        assertTrue(provider.isValid(provider.createToken()));
    }

    @Test
    void rejectsAUserAccessToken() {
        String userToken = new JwtTokenProvider(JWT).createAccessToken(7L);

        assertFalse(providerWith("$2a$10$abc", Duration.ofHours(4)).isValid(userToken));
    }

    @Test
    void userParserRejectsAnAdminToken() {
        String adminToken = providerWith("$2a$10$abc", Duration.ofHours(4)).createToken();

        assertThrows(RuntimeException.class, () -> new JwtTokenProvider(JWT).parseUserId(adminToken));
    }

    @Test
    void changingThePasswordEndsOpenSessions() {
        String token = providerWith("$2a$10$abc", Duration.ofHours(4)).createToken();

        assertFalse(providerWith("$2a$10$different", Duration.ofHours(4)).isValid(token));
    }

    @Test
    void rejectsAnExpiredToken() {
        AdminTokenProvider provider = providerWith("$2a$10$abc", Duration.ofMillis(1));
        String token = provider.createToken();

        try {
            Thread.sleep(20);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }

        assertFalse(provider.isValid(token));
    }

    @Test
    void nothingIsValidWhileNoPasswordIsConfigured() {
        AdminTokenProvider enabled = providerWith("$2a$10$abc", Duration.ofHours(4));
        AdminTokenProvider disabled = providerWith("", Duration.ofHours(4));

        assertFalse(disabled.isValid(enabled.createToken()));
        assertFalse(disabled.isValid(disabled.createToken()));
    }

    @Test
    void rejectsGarbage() {
        assertFalse(providerWith("$2a$10$abc", Duration.ofHours(4)).isValid("not-a-jwt"));
    }
}
