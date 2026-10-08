package com.wattpilot.auth.repository;

import com.wattpilot.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revokes a token only if nobody revoked it first. The single conditional UPDATE is what makes a
     * rotation one-time: of several concurrent requests carrying the same token, exactly one sees a
     * row count of 1.
     *
     * @return 1 if this call revoked the token, 0 if it was already revoked
     */
    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.id = :id and t.revokedAt is null")
    int revokeIfActive(@Param("id") Long id, @Param("now") OffsetDateTime now);

    /**
     * Deletes tokens whose session has ended before {@code cutoff}. Revoked-but-unexpired rows are kept
     * on purpose: reuse detection needs them to recognise a replayed token.
     */
    @Modifying
    @Query("delete from RefreshToken t where t.absoluteExpiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") OffsetDateTime cutoff);

    /** Ends every live session of a user, used when a stolen refresh token is suspected. */
    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
    int revokeAllActiveForUser(@Param("userId") Long userId, @Param("now") OffsetDateTime now);
}
