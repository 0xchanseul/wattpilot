package com.wattpilot.common.response;

import org.springframework.data.domain.Page;

/**
 * Pagination envelope for a list response. Matches the {@code PageMetadata} schema in
 * docs/openapi.yaml.
 *
 * <p>Spring Data's own {@code Page} JSON shape is deliberately not exposed; this record is the
 * stable contract and {@link #from(Page)} adapts a repository result onto it.
 */
public record PageMetadata(
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    public static PageMetadata from(Page<?> page) {
        return new PageMetadata(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
