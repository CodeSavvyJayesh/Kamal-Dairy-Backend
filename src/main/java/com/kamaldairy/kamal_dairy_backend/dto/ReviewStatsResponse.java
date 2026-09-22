package com.kamaldairy.kamal_dairy_backend.dto;

/** Admin Reviews tab header. low = published with 1 or 2 stars. */
public record ReviewStatsResponse(
        long published,
        long hidden,
        Double average,
        long low,
        long unanswered
) {}
