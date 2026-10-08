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

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Signs and verifies the {@code state} value that round-trips through Smartcar Connect.
 *
 * <p>Every vehicle-connection endpoint is authenticated (no public Smartcar callback is needed;
 * see the plan), so {@code state} does not have to defend against CSRF on its own. Its job is to
 * survive the redirect intact and prove, back on WattPilot's own endpoints, which user and EV the
 * Connect attempt was for. It is a signed, stateless, short-lived JWT signed with a key derived from
 * the existing JWT secret ({@link JwtProperties}) rather than a new secret or a server-side
 * pending-state table. The derived key keeps it from being accepted as an access token.
 */
@Service
public class SmartcarConnectStateService {

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_EV_ID = "evId";
    private static final String TYPE_VALUE = "smartcar-connect";
    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final String KEY_DERIVATION_LABEL = "wattpilot:smartcar-connect-state";

    private final SecretKey key;
    private final String issuer;

    public SmartcarConnectStateService(JwtProperties jwtProperties) {
        this.key = deriveKey(Decoders.BASE64.decode(jwtProperties.secret()));
        this.issuer = jwtProperties.issuer();
    }

    /**
     * The state travels through the browser's address bar, so it ends up in access logs and browser
     * history. Signing it with a key derived from, but different to, the access-token key means a
     * leaked state can never be presented as an access token.
     */
    private static SecretKey deriveKey(byte[] jwtSecret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(jwtSecret, "HmacSHA256"));
            return Keys.hmacShaKeyFor(mac.doFinal(KEY_DERIVATION_LABEL.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 is not available in this JVM", ex);
        }
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
