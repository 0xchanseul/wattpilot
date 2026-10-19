package com.wattpilot.visit.service;

import com.wattpilot.visit.VisitProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrafficSourceClassifierTest {

    private final TrafficSourceClassifier classifier =
            new TrafficSourceClassifier(new VisitProperties(List.of("wattpilot.dev", "localhost"), null));

    @ParameterizedTest
    @CsvSource({
            "https://www.linkedin.com/feed/update/123?x=1, linkedin",
            "https://lnkd.in/abc, linkedin",
            "https://www.reddit.com/r/norway/, reddit",
            "https://old.reddit.com/, reddit",
            "https://www.google.com/, google",
            "https://www.google.co.kr/search?q=wattpilot, google",
            "https://t.co/xyz, x",
            "https://news.ycombinator.com/item?id=1, hackernews",
            "https://blog.example.org/post/1, blog.example.org",
    })
    void classifiesAReferrerByItsHost(String referrer, String expected) {
        assertEquals(expected, classifier.classify(referrer, null));
    }

    @ParameterizedTest
    @CsvSource({
            "https://wattpilot.dev/login",
            "https://www.wattpilot.dev/",
            "http://localhost:5173/",
            "not a url",
            "javascript:alert(1)",
            "ftp://files.example.org/",
    })
    void treatsOwnSiteAndUnusableReferrersAsDirect(String referrer) {
        assertEquals("direct", classifier.classify(referrer, null));
    }

    @Test
    void emptyReferrerIsDirect() {
        assertEquals("direct", classifier.classify(null, null));
        assertEquals("direct", classifier.classify("", null));
        assertEquals("direct", classifier.classify("  ", null));
    }

    @Test
    void utmSourceWinsOverTheReferrerAndIsLowercased() {
        assertEquals("linkedin", classifier.classify("https://www.reddit.com/", "LinkedIn"));
    }

    @Test
    void anUnsafeUtmSourceIsIgnored() {
        assertEquals("reddit", classifier.classify("https://www.reddit.com/", "<script>alert(1)</script>"));
        assertEquals("direct", classifier.classify(null, "x".repeat(80)));
    }

    @Test
    void anUnknownHostIsStoredWithoutPathOrQuery() {
        assertEquals("example.org", classifier.classify("https://example.org/private/path?token=secret", null));
    }
}
