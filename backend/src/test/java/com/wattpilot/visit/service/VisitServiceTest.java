package com.wattpilot.visit.service;

import com.wattpilot.common.security.JwtProperties;
import com.wattpilot.visit.VisitProperties;
import com.wattpilot.visit.repository.DailyVisitorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VisitServiceTest {

    private static final String BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";

    @Mock
    private DailyVisitorRepository repository;

    @Mock
    private CountryResolver countryResolver;

    private final TrafficSourceClassifier classifier =
            new TrafficSourceClassifier(new VisitProperties(java.util.List.of("wattpilot.dev"), null));

    private VisitService serviceAt(String instant) {
        Clock clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
        JwtProperties jwt = new JwtProperties(
                Base64.getEncoder().encodeToString(new byte[32]), "wattpilot",
                Duration.ofMinutes(30), Duration.ofDays(7), Duration.ofDays(30));
        return new VisitService(repository, classifier, countryResolver, clock, jwt);
    }

    @Test
    void recordsOneHashedRowForABrowserVisit() {
        serviceAt("2026-10-17T10:00:00Z").recordVisit("203.0.113.7", BROWSER_USER_AGENT, null, null);

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(repository).insertIfAbsent(any(LocalDate.class), hash.capture(), any(), any());
        assertEquals(64, hash.getValue().length());
        assertTrue(!hash.getValue().contains("203.0.113.7"));
    }

    @Test
    void usesTheOsloCalendarDayNotTheUtcDay() {
        // 22:30 UTC on 17 Oct is already 00:30 on 18 Oct in Oslo (UTC+2).
        serviceAt("2026-10-17T22:30:00Z").recordVisit("203.0.113.7", BROWSER_USER_AGENT, null, null);

        verify(repository).insertIfAbsent(org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 10, 18)), any(), any(), any());
    }

    @Test
    void sameVisitorGetsTheSameHashWithinADayButADifferentOneTheNextDay() {
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);

        serviceAt("2026-10-17T10:00:00Z").recordVisit("203.0.113.7", BROWSER_USER_AGENT, null, null);
        serviceAt("2026-10-17T15:00:00Z").recordVisit("203.0.113.7", BROWSER_USER_AGENT, null, null);
        serviceAt("2026-10-18T10:00:00Z").recordVisit("203.0.113.7", BROWSER_USER_AGENT, null, null);

        verify(repository, org.mockito.Mockito.times(3)).insertIfAbsent(any(), hash.capture(), any(), any());
        assertEquals(hash.getAllValues().get(0), hash.getAllValues().get(1));
        assertNotEquals(hash.getAllValues().get(0), hash.getAllValues().get(2));
    }

    @Test
    void skipsCrawlersScriptsAndMissingUserAgents() {
        VisitService service = serviceAt("2026-10-17T10:00:00Z");

        service.recordVisit("203.0.113.7", "Mozilla/5.0 (compatible; Googlebot/2.1)", null, null);
        service.recordVisit("203.0.113.7", "curl/8.4.0", null, null);
        service.recordVisit("203.0.113.7", "python-requests/2.31", null, null);
        service.recordVisit("203.0.113.7", " ", null, null);
        service.recordVisit("203.0.113.7", null, null, null);

        verify(repository, never()).insertIfAbsent(any(), any(), any(), any());
    }

    @Test
    void storesTheSourceAndCountryOfTheVisit() {
        org.mockito.Mockito.when(countryResolver.resolve("203.0.113.7")).thenReturn("NO");

        serviceAt("2026-10-17T10:00:00Z")
                .recordVisit("203.0.113.7", BROWSER_USER_AGENT, "https://www.linkedin.com/feed/", null);

        verify(repository).insertIfAbsent(any(), any(), org.mockito.ArgumentMatchers.eq("linkedin"),
                org.mockito.ArgumentMatchers.eq("NO"));
    }

    @Test
    void ipv6AddressesOfTheSameNetworkCountAsOneVisitor() {
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);

        VisitService service = serviceAt("2026-10-17T10:00:00Z");
        service.recordVisit("2001:db8:1:2:aaaa:bbbb:cccc:dddd", BROWSER_USER_AGENT, null, null);
        service.recordVisit("2001:db8:1:2:1111:2222:3333:4444", BROWSER_USER_AGENT, null, null);
        service.recordVisit("2001:db8:1:3:aaaa:bbbb:cccc:dddd", BROWSER_USER_AGENT, null, null);

        verify(repository, org.mockito.Mockito.times(3)).insertIfAbsent(any(), hash.capture(), any(), any());
        assertEquals(hash.getAllValues().get(0), hash.getAllValues().get(1));
        assertNotEquals(hash.getAllValues().get(0), hash.getAllValues().get(2));
    }
}
