package com.kamaldairy.kamal_dairy_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SubscriptionPreviewResponse(
        Integer productId,
        String productName,
        String productImageUrl,
        String plan,
        String planName,
        String frequencyLabel,
        int discountPercent,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal pricePerDelivery,
        BigDecimal savingsPerDelivery,
        int deliveriesPerMonth,
        BigDecimal estimatedMonthly,
        BigDecimal estimatedMonthlySavings,
        LocalDate startDate,
        List<LocalDate> upcomingDates
) {}
