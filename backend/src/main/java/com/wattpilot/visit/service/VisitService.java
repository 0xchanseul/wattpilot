package com.wattpilot.visit.service;

import com.wattpilot.common.security.JwtProperties;
import com.wattpilot.visit.repository.DailyVisitorRepository;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
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
 *
 * <p>The first visit of a day also records where the visitor came from and, when a country database
 * is configured, which country the address belongs to. Neither can identify a person.
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
    private final TrafficSourceClassifier trafficSourceClassifier;
    private final CountryResolver countryResolver;
    private final Clock clock;
    private final SecretKeySpec hashKey;

    public VisitService(DailyVisitorRepository dailyVisitorRepository,
                        TrafficSourceClassifier trafficSourceClassifier,
                        CountryResolver countryResolver,
                        Clock clock,
                        JwtProperties jwtProperties) {
        this.dailyVisitorRepository = dailyVisitorRepository;
        this.trafficSourceClassifier = trafficSourceClassifier;
        this.countryResolver = countryResolver;
        this.clock = clock;
        byte[] secret = Base64.getDecoder().decode(jwtProperties.secret());
        byte[] key = (KEY_CONTEXT + HexFormat.of().formatHex(secret)).getBytes(StandardCharsets.UTF_8);
        this.hashKey = new SecretKeySpec(key, "HmacSHA256");
    }

    public void recordVisit(String clientIp, String userAgent, String referrer, String utmSource) {
        if (isNonHuman(userAgent)) {
            return;
        }
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        dailyVisitorRepository.insertIfAbsent(
                today,
                hash(today, visitorNetwork(clientIp), userAgent),
                trafficSourceClassifier.classify(referrer, utmSource),
                countryResolver.resolve(clientIp));
    }

    /**
     * An IPv6 device rotates the lower 64 bits of its address (privacy extensions), so hashing the
     * full address would count one device several times a day. The /64 prefix identifies the network.
     */
    private static String visitorNetwork(String clientIp) {
        if (clientIp == null || clientIp.indexOf(':') < 0) {
            return clientIp;
        }
        try {
            byte[] address = InetAddress.getByName(clientIp).getAddress();
            if (address.length != 16) {
                return clientIp;
            }
            return HexFormat.of().formatHex(Arrays.copyOf(address, 8)) + "/64";
        } catch (UnknownHostException ex) {
            return clientIp;
        }
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
