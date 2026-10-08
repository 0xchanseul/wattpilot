package com.wattpilot.auth;

import com.wattpilot.auth.service.RefreshTokenCleanupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the cleanup against a real PostgreSQL instance so the delete query and its cutoff are verified. */
@SpringBootTest
@Testcontainers
class RefreshTokenCleanupIntegrationTest {

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    @Autowired
    private RefreshTokenCleanupService cleanupService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void deletesTokensExpiredBeyondTheRetentionAndKeepsTheRest() {
        long userId = jdbcTemplate.queryForObject("""
                INSERT INTO users (email, password_hash, name, default_price_area, created_at, updated_at)
                VALUES ('refresh-cleanup@example.com', 'hash', 'Cleanup', 'NO1', now(), now())
                RETURNING id
                """, Long.class);
        insertToken(userId, "expired-long-ago", "10 days", "-5 days", null);
        insertToken(userId, "expired-just-now", "3 days", "-2 hours", null);
        insertToken(userId, "still-valid", "1 day", "+6 days", null);
        insertToken(userId, "revoked-but-unexpired", "1 day", "+6 days", "1 hour");

        int deleted = cleanupService.deleteExpiredTokens();

        assertThat(deleted).isEqualTo(1);
        assertThat(exists("expired-long-ago")).isFalse();
        assertThat(exists("expired-just-now")).isTrue();
        assertThat(exists("still-valid")).isTrue();
        assertThat(exists("revoked-but-unexpired")).isTrue();
    }

    private void insertToken(long userId, String hash, String createdAgo, String expiresOffset, String revokedAgo) {
        jdbcTemplate.update("""
                INSERT INTO refresh_tokens (user_id, token_hash, created_at, expires_at, revoked_at)
                VALUES (?, ?, now() - CAST(? AS interval), now() + CAST(? AS interval),
                        now() - CAST(? AS interval))
                """, userId, hash, createdAgo, expiresOffset, revokedAgo);
    }

    private boolean exists(String hash) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE token_hash = ?", Integer.class, hash);
        return count != null && count > 0;
    }
}
