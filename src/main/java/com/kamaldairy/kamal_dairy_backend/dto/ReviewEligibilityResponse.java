package com.kamaldairy.kamal_dairy_backend.dto;

/** Can the caller review this product, and their review if they already wrote one. */
public record ReviewEligibilityResponse(
        Integer productId,
        boolean eligible,
        String reason,
        ReviewResponse review
) {}
