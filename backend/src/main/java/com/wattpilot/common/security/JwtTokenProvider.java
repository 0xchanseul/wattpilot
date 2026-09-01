package com.wattpilot.common.security;

import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and verifies the HS256 access tokens used for stateless authentication.
 *
 * <p>A token carries only the registered claims {@code sub}, {@code iss}, {@code iat},
 * {@code exp} and {@code jti}. A JWT payload is signed but not encrypted, so no profile data
 * is embedded: the signature protects against tampering, not against reading.
 */
public class JwtTokenProvider {

    private final SecretKey key;
    private final String issuer;
    private final Duration accessTokenTtl;

    public JwtTokenProvider(JwtProperties properties) {
        // Rejects a key shorter than 256 bits, so a weak secret fails at startup rather than
        // silently weakening every issued token.
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
        this.issuer = properties.issuer();
        this.accessTokenTtl = properties.accessTokenTtl();
    }

    public String createAccessToken(Long userId) {
        Instant issuedAt = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(accessTokenTtl)))
                .signWith(key)
                .compact();
    }

    /**
     * @throws BusinessException with {@code TOKEN_EXPIRED} or {@code INVALID_TOKEN}
     */
    public Long parseUserId(String accessToken) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(accessToken)
                    .getPayload();
            return Long.valueOf(claims.getSubject());
        } catch (ExpiredJwtException ex) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }

    public long accessTokenTtlSeconds() {
        return accessTokenTtl.toSeconds();
    }
}
