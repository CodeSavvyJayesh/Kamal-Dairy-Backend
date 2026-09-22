package com.kamaldairy.kamal_dairy_backend.dto;

import java.util.List;

/** Rating summary for a product plus one page of its published reviews. */
public record ProductReviewsResponse(
        Integer productId,
        Double average,
        long count,
        List<Bucket> distribution,
        PageResponse<ReviewResponse> reviews
) {
    /** stars 5 down to 1, with how many reviews gave that many. */
    public record Bucket(int stars, long count) {}
}
