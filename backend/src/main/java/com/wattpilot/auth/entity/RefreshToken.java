package com.wattpilot.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * A single issued refresh token, stored only as a hash.
 *
 * <p>The owning user is referenced by id rather than by an association, so the auth module does
 * not depend on the user entity to rotate or revoke a token.
 *
 * <p>Rotation and logout revoke rather than delete: a row that disappeared would be
 * indistinguishable from one that was never issued, whereas {@code revokedAt} keeps the replay of
 * a superseded token visible in the logs.
 *
 * <p>{@code absoluteExpiresAt} is the end of the whole login session, fixed at the initial login.
 * Rotation copies it onto the successor row unchanged, so a session cannot be kept alive
 * indefinitely by refreshing. It is stored in the {@code expires_at} column.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime absoluteExpiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    private RefreshToken(Long userId, String tokenHash, OffsetDateTime absoluteExpiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.absoluteExpiresAt = absoluteExpiresAt;
    }

    public static RefreshToken issue(Long userId, String tokenHash, OffsetDateTime absoluteExpiresAt) {
        return new RefreshToken(userId, tokenHash, absoluteExpiresAt);
    }

    public void revoke(OffsetDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(OffsetDateTime at) {
        return !absoluteExpiresAt.isAfter(at);
    }
}
