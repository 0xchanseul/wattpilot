package com.wattpilot.auth.service;

import com.wattpilot.auth.dto.LoginRequest;
import com.wattpilot.auth.entity.RefreshToken;
import com.wattpilot.auth.repository.RefreshTokenRepository;
import com.wattpilot.common.PriceArea;
import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import com.wattpilot.common.security.JwtProperties;
import com.wattpilot.common.security.JwtTokenProvider;
import com.wattpilot.user.entity.User;
import com.wattpilot.user.entity.UserStatus;
import com.wattpilot.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    private static final String RAW_PASSWORD = "correct-horse-battery";
    private static final String PRESENTED_TOKEN = "presented-refresh-token";

    @Mock
    private UserService userService;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    // Strength 4 keeps the suite fast while still exercising real hash verification.
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private AuthService authService;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties(
                Base64.getEncoder().encodeToString(new byte[32]),
                "wattpilot",
                Duration.ofHours(1),
                Duration.ofDays(14));
        authService = new AuthService(
                userService, refreshTokenRepository, jwtTokenProvider, passwordEncoder, jwtProperties);

        when(jwtTokenProvider.createAccessToken(any())).thenReturn("access-token");
        when(jwtTokenProvider.accessTokenTtlSeconds()).thenReturn(3600L);
    }

    @Test
    void loginWithUnknownEmailIsRejectedAsInvalidCredentials() {
        when(userService.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", RAW_PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void loginWithWrongPasswordReportsTheSameErrorAsAnUnknownEmail() {
        when(userService.findByEmail(anyString())).thenReturn(Optional.of(activeUser()));

        assertThatThrownBy(() -> authService.login(new LoginRequest("iris@example.com", "wrong-password")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void loginOnDeactivatedAccountDoesNotRevealThatTheAccountExists() {
        User user = activeUser();
        ReflectionTestUtils.setField(user, "status", UserStatus.INACTIVE);
        when(userService.findByEmail(anyString())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("iris@example.com", RAW_PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void loginWithValidCredentialsReturnsATokenPairAndTheUser() {
        when(userService.findByEmail(anyString())).thenReturn(Optional.of(activeUser()));

        var response = authService.login(new LoginRequest("iris@example.com", RAW_PASSWORD));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600L);
        assertThat(response.user().email()).isEqualTo("iris@example.com");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void refreshRevokesThePresentedTokenAndStoresANewOne() {
        RefreshToken stored = storedToken(1L, OffsetDateTime.now(ZoneOffset.UTC).plusDays(14));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));
        when(userService.getById(1L)).thenReturn(activeUser());

        var response = authService.refresh(PRESENTED_TOKEN);

        assertThat(stored.isRevoked()).isTrue();
        assertThat(response.refreshToken()).isNotBlank();
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void replayingAnAlreadyRotatedTokenIsRejected() {
        RefreshToken stored = storedToken(1L, OffsetDateTime.now(ZoneOffset.UTC).plusDays(14));
        stored.revoke(OffsetDateTime.now(ZoneOffset.UTC));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(PRESENTED_TOKEN))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void expiredRefreshTokenIsReportedAsExpired() {
        RefreshToken stored = storedToken(1L, OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(PRESENTED_TOKEN))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.TOKEN_EXPIRED);
    }

    @Test
    void unknownRefreshTokenIsRejected() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(PRESENTED_TOKEN))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void logoutRevokesOnlyTheSessionOfTheCallingUser() {
        RefreshToken stored = storedToken(1L, OffsetDateTime.now(ZoneOffset.UTC).plusDays(14));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        authService.logout(2L, PRESENTED_TOKEN);

        assertThat(stored.isRevoked()).isFalse();
    }

    @Test
    void logoutIsIdempotentForATokenThatIsNoLongerStored() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        authService.logout(1L, PRESENTED_TOKEN);
    }

    private User activeUser() {
        User user = User.register("iris@example.com", passwordEncoder.encode(RAW_PASSWORD), "Iris", PriceArea.NO1);
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    private static RefreshToken storedToken(Long userId, OffsetDateTime expiresAt) {
        RefreshToken token = RefreshToken.issue(userId, "stored-hash", expiresAt);
        ReflectionTestUtils.setField(token, "id", 10L);
        return token;
    }
}
