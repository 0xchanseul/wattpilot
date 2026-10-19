package com.wattpilot.visit.service;

import com.wattpilot.visit.VisitProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reduces what a browser reports about where a visit came from to one short label.
 *
 * <p>An explicit {@code utm_source} wins over the referrer, because a campaign link states the
 * intended channel even when the referring site strips the referrer. Only the host of a referrer is
 * kept: a path or query string can carry personal data and is not needed for the statistics.
 */
@Component
public class TrafficSourceClassifier {

    public static final String DIRECT = "direct";

    private static final int MAX_REFERRER_LENGTH = 2048;
    private static final int MAX_SOURCE_LENGTH = 100;

    // The value ends up in the database, so only a conservative character set is accepted.
    private static final Pattern UTM_SOURCE = Pattern.compile("[a-z0-9_.-]{1,50}");
    private static final Pattern GOOGLE_HOST = Pattern.compile("(^|.*\\.)google\\.[a-z.]{2,6}");

    // Registered host suffix -> label. A host matches when it equals the suffix or is a subdomain of it.
    private static final Map<String, String> KNOWN_HOSTS = knownHosts();

    private final VisitProperties properties;

    public TrafficSourceClassifier(VisitProperties properties) {
        this.properties = properties;
    }

    public String classify(String referrer, String utmSource) {
        String utm = normalizeUtmSource(utmSource);
        if (utm != null) {
            return utm;
        }
        String host = referrerHost(referrer);
        if (host == null || isOwnHost(host)) {
            return DIRECT;
        }
        if (GOOGLE_HOST.matcher(host).matches()) {
            return "google";
        }
        for (Map.Entry<String, String> known : KNOWN_HOSTS.entrySet()) {
            if (host.equals(known.getKey()) || host.endsWith("." + known.getKey())) {
                return known.getValue();
            }
        }
        return host.length() > MAX_SOURCE_LENGTH ? host.substring(0, MAX_SOURCE_LENGTH) : host;
    }

    private static String normalizeUtmSource(String utmSource) {
        if (utmSource == null) {
            return null;
        }
        String value = utmSource.trim().toLowerCase(Locale.ROOT);
        return UTM_SOURCE.matcher(value).matches() ? value : null;
    }

    private static String referrerHost(String referrer) {
        if (referrer == null || referrer.isBlank() || referrer.length() > MAX_REFERRER_LENGTH) {
            return null;
        }
        try {
            URI uri = URI.create(referrer.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (host == null || scheme == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return null;
            }
            String normalized = host.toLowerCase(Locale.ROOT);
            return normalized.startsWith("www.") ? normalized.substring(4) : normalized;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean isOwnHost(String host) {
        return properties.ownHosts().stream()
                .map(own -> own.trim().toLowerCase(Locale.ROOT))
                .anyMatch(own -> host.equals(own) || host.endsWith("." + own));
    }

    private static Map<String, String> knownHosts() {
        Map<String, String> hosts = new LinkedHashMap<>();
        hosts.put("linkedin.com", "linkedin");
        hosts.put("lnkd.in", "linkedin");
        hosts.put("reddit.com", "reddit");
        hosts.put("redd.it", "reddit");
        hosts.put("bing.com", "bing");
        hosts.put("duckduckgo.com", "duckduckgo");
        hosts.put("naver.com", "naver");
        hosts.put("twitter.com", "x");
        hosts.put("x.com", "x");
        hosts.put("t.co", "x");
        hosts.put("facebook.com", "facebook");
        hosts.put("fb.com", "facebook");
        hosts.put("instagram.com", "instagram");
        hosts.put("github.com", "github");
        hosts.put("ycombinator.com", "hackernews");
        hosts.put("youtube.com", "youtube");
        hosts.put("youtu.be", "youtube");
        return Map.copyOf(hosts);
    }
}
