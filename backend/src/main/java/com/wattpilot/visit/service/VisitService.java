package com.wattpilot.visit.service;

import com.wattpilot.common.security.JwtProperties;
import com.wattpilot.visit.repository.DailyVisitorRepository;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Counts each distinct visitor once per Europe/Oslo day, without keeping the visitor's address.
 *
 * <p>The stored value is an HMAC of the day, IP and User-Agent. The key is derived from the
 * application secret because a plain hash of an IPv4 address can be reversed by trying every
 * address. Including the day makes the value different every day, so the table cannot be used to
 * follow one visitor over time.
 */
@Service
public class VisitService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Oslo");
    private static final String KEY_CONTEXT = "wattpilot-visit:";

    // Crawlers, uptime monitors and scripted clients; they would otherwise inflate the count.
    private static final Pattern NON_HUMAN_USER_AGENT = Pattern.compile(
            "bot|crawl|spider|slurp|headless|monitor|uptime|curl|wget|python|java/|go-http|okhttp|"
                    + "httpclient|preview|lighthouse",
            Pattern.CASE_INSENSITIVE);

    private final DailyVisitorRepository dailyVisitorRepository;
    private final Clock clock;
    private final SecretKeySpec hashKey;

    public VisitService(DailyVisitorRepository dailyVisitorRepository, Clock clock, JwtProperties jwtProperties) {
        this.dailyVisitorRepository = dailyVisitorRepository;
        this.clock = clock;
        byte[] secret = Base64.getDecoder().decode(jwtProperties.secret());
        byte[] key = (KEY_CONTEXT + HexFormat.of().formatHex(secret)).getBytes(StandardCharsets.UTF_8);
        this.hashKey = new SecretKeySpec(key, "HmacSHA256");
    }

    public void recordVisit(String clientIp, String userAgent) {
        if (isNonHuman(userAgent)) {
            return;
        }
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        dailyVisitorRepository.insertIfAbsent(today, hash(today, clientIp, userAgent));
    }

    private static boolean isNonHuman(String userAgent) {
        return userAgent == null || userAgent.isBlank() || NON_HUMAN_USER_AGENT.matcher(userAgent).find();
    }

    private String hash(LocalDate day, String clientIp, String userAgent) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hashKey);
            byte[] digest = mac.doFinal((day + "|" + clientIp + "|" + userAgent).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 is unavailable", ex);
        }
    }
}
