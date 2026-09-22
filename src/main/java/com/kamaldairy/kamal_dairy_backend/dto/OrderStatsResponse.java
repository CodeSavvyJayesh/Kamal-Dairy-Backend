package com.kamaldairy.kamal_dairy_backend.dto;

/** Counts for the admin Orders tab. open = placed + confirmed + out for delivery. */
public record OrderStatsResponse(
        long placed,
        long confirmed,
        long outForDelivery,
        long delivered,
        long cancelled,
        long open,
        long lowStockProducts
) {}
