package com.wattpilot.admin.security;

import com.wattpilot.admin.AdminProperties;
import com.wattpilot.common.security.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Issues and verifies the token that proves an admin login.
 *
 * <p>It is signed with the application key but is kept apart from user access tokens in two ways: its
 * subject is not a user id, so the user token parser rejects it, and it carries an audience that a
 * user token lacks, so this class rejects a user token. It also embeds a fingerprint of the password
 * hash, so changing the admin password ends every admin login that is still open.
 */
public class AdminTokenProvider {

    public static final String COOKIE_NAME = "wp_admin_session";
    public static final String COOKIE_PATH = "/api/v1/admin";

    private static final String SUBJECT = "admin";
    private static final String AUDIENCE = "wattpilot-admin";
    private static final String PASSWORD_VERSION_CLAIM = "pv";

    private final SecretKey key;
    private final String issuer;
    private final AdminProperties adminProperties;

    public AdminTokenProvider(JwtProperties jwtProperties, AdminProperties adminProperties) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secret()));
        this.issuer = jwtProperties.issuer();
        this.adminProperties = adminProperties;
    }

    public String createToken() {
        Instant issuedAt = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(SUBJECT)
                .audience().add(AUDIENCE).and()
                .claim(PASSWORD_VERSION_CLAIM, passwordVersion())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(adminProperties.sessionTtl())))
                .signWith(key)
                .compact();
    }

    public boolean isValid(String token) {
        if (!adminProperties.enabled()) {
            return false;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .requireSubject(SUBJECT)
                    .requireAudience(AUDIENCE)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getExpiration() != null
                    && passwordVersion().equals(claims.get(PASSWORD_VERSION_CLAIM, String.class));
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    private String passwordVersion() {
        if (!adminProperties.enabled()) {
            return "";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(adminProperties.encodedPasswordHash().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available in this JVM", ex);
        }
    }
}
