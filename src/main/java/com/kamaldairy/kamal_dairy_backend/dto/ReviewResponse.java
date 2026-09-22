package com.kamaldairy.kamal_dairy_backend.dto;

import java.time.LocalDateTime;

/** A review as customers see it. Never carries the author's email. */
public record ReviewResponse(
        Long id,
        Integer productId,
        String productName,
        int rating,
        String title,
        String body,
        String authorName,
        String verifiedVia,
        String status,
        boolean edited,
        String reply,
        LocalDateTime repliedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
