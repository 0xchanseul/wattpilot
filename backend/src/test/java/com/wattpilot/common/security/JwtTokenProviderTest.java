package com.wattpilot.common.security;

import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String SECRET = base64Key((byte) 0x2A);
    private static final String OTHER_SECRET = base64Key((byte) 0x7B);

    private final JwtTokenProvider provider = providerWith(SECRET, "wattpilot", Duration.ofHours(1));

    @Test
    void issuedTokenParsesBackToTheSameUserId() {
        String token = provider.createAccessToken(42L);

        assertThat(provider.parseUserId(token)).isEqualTo(42L);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String forged = providerWith(OTHER_SECRET, "wattpilot", Duration.ofHours(1)).createAccessToken(42L);

        assertThatThrownBy(() -> provider.parseUserId(forged))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        String foreign = providerWith(SECRET, "somewhere-else", Duration.ofHours(1)).createAccessToken(42L);

        assertThatThrownBy(() -> provider.parseUserId(foreign))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    /**
     * An expired token is reported separately from an invalid one so a client can tell that it
     * should refresh rather than log in again.
     */
    @Test
    void expiredTokenIsReportedAsExpired() {
        String expired = providerWith(SECRET, "wattpilot", Duration.ofMinutes(-5)).createAccessToken(42L);

        assertThatThrownBy(() -> provider.parseUserId(expired))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.TOKEN_EXPIRED);
    }

    @Test
    void malformedTokenIsRejected() {
        assertThatThrownBy(() -> provider.parseUserId("not-a-jwt"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void keyShorterThanTheHs256MinimumIsRejectedOnStartup() {
        String tooShort = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> providerWith(tooShort, "wattpilot", Duration.ofHours(1)))
                .isInstanceOf(RuntimeException.class);
    }

    private static JwtTokenProvider providerWith(String secret, String issuer, Duration accessTokenTtl) {
        return new JwtTokenProvider(new JwtProperties(secret, issuer, accessTokenTtl, Duration.ofDays(14)));
    }

    private static String base64Key(byte filler) {
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, filler);
        return Base64.getEncoder().encodeToString(key);
    }
}
