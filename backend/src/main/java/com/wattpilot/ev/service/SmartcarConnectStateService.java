package com.wattpilot.ev.service;

import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import com.wattpilot.common.security.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Signs and verifies the {@code state} value that round-trips through Smartcar Connect.
 *
 * <p>Every vehicle-connection endpoint is authenticated (no public Smartcar callback is needed;
 * see the plan), so {@code state} does not have to defend against CSRF on its own. Its job is to
 * survive the redirect intact and prove, back on WattPilot's own endpoints, which user and EV the
 * Connect attempt was for. It is a signed, stateless, short-lived JWT reusing the existing JWT
 * signing key ({@link JwtProperties}) rather than a new secret or a server-side pending-state table.
 */
@Service
public class SmartcarConnectStateService {

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_EV_ID = "evId";
    private static final String TYPE_VALUE = "smartcar-connect";
    private static final Duration STATE_TTL = Duration.ofMinutes(10);

    private final SecretKey key;
    private final String issuer;

    public SmartcarConnectStateService(JwtProperties jwtProperties) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secret()));
        this.issuer = jwtProperties.issuer();
    }

    public String issue(Long userId, Long evId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .claim(CLAIM_TYPE, TYPE_VALUE)
                .claim(CLAIM_EV_ID, String.valueOf(evId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(STATE_TTL)))
                .signWith(key)
                .compact();
    }

    /**
     * @throws BusinessException with {@code INVALID_VEHICLE_CONNECT_STATE} if the state is missing,
     *         expired, tampered, not a Smartcar-connect state, or was not issued for {@code userId}
     */
    public Verified verify(String state, Long userId) {
        if (state == null || state.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_VEHICLE_CONNECT_STATE);
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(state)
                    .getPayload();
            if (!TYPE_VALUE.equals(claims.get(CLAIM_TYPE, String.class))) {
                throw new BusinessException(ErrorCode.INVALID_VEHICLE_CONNECT_STATE);
            }
            Long stateUserId = Long.valueOf(claims.getSubject());
            if (!stateUserId.equals(userId)) {
                throw new BusinessException(ErrorCode.INVALID_VEHICLE_CONNECT_STATE);
            }
            Long evId = Long.valueOf(claims.get(CLAIM_EV_ID, String.class));
            return new Verified(stateUserId, evId);
        } catch (BusinessException ex) {
            throw ex;
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_VEHICLE_CONNECT_STATE);
        }
    }

    public record Verified(Long userId, Long evId) {
    }
}
