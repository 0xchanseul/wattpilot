package com.wattpilot.history.dto;

import com.wattpilot.common.response.PageMetadata;

import java.util.List;

/**
 * The charging-history list payload: a {@link ChargingHistorySummary} header plus a page of
 * {@link ChargingHistoryItem}s.
 *
 * <p>{@code content} + {@code page} follow the project's standard pagination shape (as
 * {@code PageResponse} does); {@code summary} is the addition that makes History a "how am I doing"
 * screen rather than a filtered schedule list. The summary is scope-level (respects {@code evId},
 * ignores {@code status} and pagination).
 */
public record ChargingHistoryListResponse(
        ChargingHistorySummary summary,
        List<ChargingHistoryItem> content,
        PageMetadata page
) {
}
