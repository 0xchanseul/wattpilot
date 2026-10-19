package com.wattpilot.visit.service;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.wattpilot.visit.VisitProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.regex.Pattern;

/**
 * Looks up the country of an IP address in a local database file. Nothing is sent over the network.
 *
 * <p>Statistics are optional, so a missing or unreadable database file only means the country stays
 * unknown; it never stops the application or a visit from being counted.
 */
@Component
public class CountryResolver {

    private static final Logger log = LoggerFactory.getLogger(CountryResolver.class);

    // Only literal addresses are looked up. InetAddress.getByName would resolve anything else
    // through DNS, and the address comes from a header.
    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
    private static final Pattern IPV6 = Pattern.compile("[0-9a-fA-F:.]{2,45}");

    private final DatabaseReader reader;

    public CountryResolver(VisitProperties properties) {
        this.reader = openReader(properties.geoipDatabase());
    }

    /** Returns the ISO 3166-1 alpha-2 code, or {@code null} when the country cannot be determined. */
    public String resolve(String ip) {
        if (reader == null || ip == null || !isLiteral(ip)) {
            return null;
        }
        try {
            return reader.tryCountry(InetAddress.getByName(ip))
                    .map(response -> response.getCountry().getIsoCode())
                    .orElse(null);
        } catch (IOException | GeoIp2Exception | RuntimeException ex) {
            log.debug("Country lookup failed: {}", ex.toString());
            return null;
        }
    }

    @PreDestroy
    void close() throws IOException {
        if (reader != null) {
            reader.close();
        }
    }

    private static boolean isLiteral(String ip) {
        return IPV4.matcher(ip).matches() || (ip.indexOf(':') >= 0 && IPV6.matcher(ip).matches());
    }

    private static DatabaseReader openReader(String path) {
        if (!StringUtils.hasText(path)) {
            log.info("No GeoIP database configured; visit countries will not be recorded");
            return null;
        }
        File file = new File(path.trim());
        if (!file.isFile()) {
            log.warn("GeoIP database file not found at the configured path; visit countries will not be recorded");
            return null;
        }
        try {
            return new DatabaseReader.Builder(file).build();
        } catch (IOException | RuntimeException ex) {
            log.warn("GeoIP database could not be opened; visit countries will not be recorded: {}", ex.toString());
            return null;
        }
    }
}
