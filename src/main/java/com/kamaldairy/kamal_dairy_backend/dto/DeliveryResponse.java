package com.kamaldairy.kamal_dairy_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DeliveryResponse(
        Long id,
        Long subscriptionId,
        String productName,
        int quantity,
        BigDecimal amount,
        int discountPercent,
        LocalDate deliveryDate,
        String slot,
        String slotLabel,
        String status,
        String note
) {}
