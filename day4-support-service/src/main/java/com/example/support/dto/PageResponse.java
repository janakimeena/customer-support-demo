package com.example.support.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Stable JSON shape for paged results. Returning Spring's {@code Page} directly would expose its internal
 * structure (and Spring Data warns against it), so the API publishes only what clients need.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.hasNext());
    }
}
