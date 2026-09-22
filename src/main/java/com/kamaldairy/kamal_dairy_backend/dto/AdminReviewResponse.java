package com.kamaldairy.kamal_dairy_backend.dto;

import java.time.LocalDateTime;

public record AdminReviewResponse(
        Long id,
        Integer productId,
        String productName,
        String userEmail,
        String authorName,
        int rating,
        String title,
        String body,
        String verifiedVia,
        String status,
        String hiddenReason,
        String reply,
        LocalDateTime repliedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
