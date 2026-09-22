package com.kamaldairy.kamal_dairy_backend.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/** Stable JSON shape for paged lists (Spring's PageImpl JSON is not a supported contract). */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }
}
