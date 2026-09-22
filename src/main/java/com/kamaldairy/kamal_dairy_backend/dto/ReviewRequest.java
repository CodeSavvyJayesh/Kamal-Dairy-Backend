package com.kamaldairy.kamal_dairy_backend.dto;

/** Create or edit the caller's own review. rating 1 to 5; title and body optional. */
public record ReviewRequest(Integer productId, Integer rating, String title, String body) {}
