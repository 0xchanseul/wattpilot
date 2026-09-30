package com.wattpilot.ev.service;

import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import com.wattpilot.common.security.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmartcarConnectStateServiceTest {

    // Any Base64 string decoding to >= 32 bytes; matches the format the app's own JWT_SECRET must have.
    private static final String SECRET = "dGVzdC1vbmx5LXNlY3JldC1kby1ub3QtdXNlLWluLXByb2Q9MTIzNDU2Nzg=";

    private final JwtProperties jwtProperties =
            new JwtProperties(SECRET, "wattpilot", Duration.ofMinutes(30), Duration.ofDays(7), Duration.ofDays(30));
    private final SmartcarConnectStateService service = new SmartcarConnectStateService(jwtProperties);

    @Test
    void issuedStateRoundTripsBackToTheSameUserAndEv() {
        String state = service.issue(7L, 42L);

        SmartcarConnectStateService.Verified verified = service.verify(state, 7L);

        assertThat(verified.userId()).isEqualTo(7L);
        assertThat(verified.evId()).isEqualTo(42L);
    }

    @Test
    void rejectsAStateIssuedForADifferentUser() {
        String state = service.issue(7L, 42L);

        assertThatThrownBy(() -> service.verify(state, 999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode())
                        .isEqualTo(ErrorCode.INVALID_VEHICLE_CONNECT_STATE));
    }

    @Test
    void rejectsATamperedState() {
        String state = service.issue(7L, 42L);
        // Flip a character inside the payload segment, not the token's last character: the last
        // base64url character of a JWT signature can encode only its data bits plus unused padding
        // bits, so flipping it can leave the decoded bytes (and thus the signature check) unchanged.
        int payloadStart = state.indexOf('.') + 1;
        char flipped = state.charAt(payloadStart) == 'A' ? 'B' : 'A';
        String tampered = state.substring(0, payloadStart) + flipped + state.substring(payloadStart + 1);

        assertThatThrownBy(() -> service.verify(tampered, 7L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsAnExpiredState() {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
        Instant past = Instant.now().minus(Duration.ofHours(1));
        String expired = Jwts.builder()
                .issuer("wattpilot")
                .subject("7")
                .claim("typ", "smartcar-connect")
                .claim("evId", "42")
                .issuedAt(Date.from(past.minus(Duration.ofMinutes(10))))
                .expiration(Date.from(past))
                .signWith(key)
                .compact();

        assertThatThrownBy(() -> service.verify(expired, 7L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsATokenOfADifferentType() {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
        Instant now = Instant.now();
        String wrongType = Jwts.builder()
                .issuer("wattpilot")
                .subject("7")
                .claim("typ", "something-else")
                .claim("evId", "42")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(10))))
                .signWith(key)
                .compact();

        assertThatThrownBy(() -> service.verify(wrongType, 7L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsABlankState() {
        assertThatThrownBy(() -> service.verify("", 7L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.verify(null, 7L)).isInstanceOf(BusinessException.class);
    }
}
