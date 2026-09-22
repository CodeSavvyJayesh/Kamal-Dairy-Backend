package com.kamaldairy.kamal_dairy_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SubscriptionStatsResponse(
        long activeSubscriptions,
        long pausedSubscriptions,
        long cancelledSubscriptions,
        LocalDate today,
        int todayDeliveries,
        LocalDate tomorrow,
        boolean tomorrowGenerated,
        int tomorrowDeliveries,
        BigDecimal tomorrowRevenue,
        BigDecimal monthlyRecurringRevenue,
        BigDecimal walletFloat,
        int cutoffHour
) {}
