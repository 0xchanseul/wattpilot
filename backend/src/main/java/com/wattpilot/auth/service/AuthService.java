package com.wattpilot.auth.service;

import com.wattpilot.auth.dto.AuthResponse;
import com.wattpilot.auth.dto.LoginRequest;
import com.wattpilot.auth.dto.SignUpRequest;
import com.wattpilot.auth.dto.TokenResponse;
import com.wattpilot.auth.entity.RefreshToken;
import com.wattpilot.auth.repository.RefreshTokenRepository;
import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import com.wattpilot.common.security.JwtProperties;
import com.wattpilot.common.security.JwtTokenProvider;
import com.wattpilot.user.dto.UserResponse;
import com.wattpilot.user.entity.User;
import com.wattpilot.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Sign-up, credential verification and refresh-token lifecycle.
 *
 * <p>Access tokens are self-contained and never stored, so they cannot be revoked before they
 * expire. Refresh tokens are the opposite: opaque, stored as a hash, and rotated on every use, so
 * a leaked refresh token is only usable until its owner next refreshes.
 */
@Service
@Transactional(readOnly = true)
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final String TOKEN_TYPE = "Bearer";
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final UserService userService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final Duration refreshTokenTtl;
    private final String unusablePasswordHash;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserService userService,
                       RefreshTokenRepository refreshTokenRepository,
                       JwtTokenProvider jwtTokenProvider,
                       PasswordEncoder passwordEncoder,
                       JwtProperties jwtProperties) {
        this.userService = userService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenTtl = jwtProperties.refreshTokenTtl();
        // Verified against when the email is unknown, so an unregistered address costs the same
        // hashing work as a wrong password and cannot be identified by response time.
        this.unusablePasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthResponse signUp(SignUpRequest request) {
        User user = userService.register(
                request.email(), request.password(), request.name(), request.defaultPriceArea());
        return AuthResponse.of(issueTokens(user), UserResponse.from(user));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userService.findByEmail(request.email()).orElse(null);
        String storedHash = user != null ? user.getPasswordHash() : unusablePasswordHash;
        boolean passwordMatches = passwordEncoder.matches(request.password(), storedHash);

        // An unknown email, a wrong password and a deactivated account are reported identically so
        // the endpoint cannot be used to find out which addresses are registered.
        if (user == null || !passwordMatches || !user.isActive()) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        return AuthResponse.of(issueTokens(user), UserResponse.from(user));
    }

    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        if (storedToken.isRevoked()) {
            // Either a replay of an already rotated token or a request from a logged-out client.
            // V1 only records it: revoking the whole family would log out the honest device too.
            log.warn("Revoked refresh token presented: tokenId={}, userId={}",
                    storedToken.getId(), storedToken.getUserId());
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        if (storedToken.isExpired(now)) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }

        User user = userService.getById(storedToken.getUserId());
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        storedToken.revoke(now);
        return issueTokens(user);
    }

    /**
     * Revokes the refresh token of the calling session only. Always succeeds: repeating a logout,
     * or sending a token that is already gone, leaves the client in the state it asked for.
     */
    @Transactional
    public void logout(Long userId, String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                // Ownership check: a valid access token must not be able to revoke another
                // account's session.
                .filter(token -> token.getUserId().equals(userId))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> token.revoke(OffsetDateTime.now(ZoneOffset.UTC)));
    }

    private TokenResponse issueTokens(User user) {
        String rawRefreshToken = generateRefreshToken();
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plus(refreshTokenTtl);
        refreshTokenRepository.save(RefreshToken.issue(user.getId(), hash(rawRefreshToken), expiresAt));

        return new TokenResponse(
                jwtTokenProvider.createAccessToken(user.getId()),
                rawRefreshToken,
                TOKEN_TYPE,
                jwtTokenProvider.accessTokenTtlSeconds());
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * A refresh token is 256 bits of randomness, not a memorable secret, so it cannot be guessed
     * from its digest. A plain SHA-256 is therefore enough, and unlike a per-row salted hash it
     * can be looked up through the unique index on {@code token_hash}.
     */
    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available in this JVM", ex);
        }
    }
}
