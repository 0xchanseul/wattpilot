package com.wattpilot.common.response;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Generic paginated list response: {@code content} plus a {@link PageMetadata} block, matching the
 * {@code *Page} schemas in docs/openapi.yaml.
 *
 * <p>Callers map the entity page to a DTO page first (e.g. {@code page.map(EvResponse::from)}), then
 * wrap it here.
 */
public record PageResponse<T>(List<T> content, PageMetadata page) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), PageMetadata.from(page));
    }
}
