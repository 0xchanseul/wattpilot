package com.wattpilot.visit.dto;

/**
 * Optional body of {@code POST /visits}. Both values come straight from the visitor's browser, so
 * they are untrusted: the server reduces them to a short label and never stores them as sent.
 *
 * @param referrer  {@code document.referrer} of the page; only its host is used
 * @param utmSource the {@code utm_source} query parameter of the landing URL
 */
public record VisitRequest(String referrer, String utmSource) {
}
