package com.kamaldairy.kamal_dairy_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SubscriptionResponse(
        Long id,
        Integer productId,
        String productName,
        String productImageUrl,
        int quantity,
        String frequency,
        String frequencyLabel,
        String plan,
        String planName,
        int discountPercent,
        List<String> daysOfWeek,
        String slot,
        String slotLabel,
        LocalDate startDate,
        String status,
        LocalDate vacationStart,
        LocalDate vacationEnd,
        LocalDate nextDeliveryDate,
        BigDecimal unitPrice,
        BigDecimal pricePerDelivery,
        BigDecimal savingsPerDelivery,
        BigDecimal estimatedMonthly,
        List<LocalDate> skippedDates,
        DeliveryAddress address,
        LocalDateTime createdAt
) {}
