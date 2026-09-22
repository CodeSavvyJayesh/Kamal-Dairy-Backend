package com.kamaldairy.kamal_dairy_backend.dto;

import java.math.BigDecimal;

public record AdminDeliveryResponse(
        Long id,
        Long subscriptionId,
        String customerEmail,
        String customerName,
        String phone,
        String address,
        String productName,
        int quantity,
        BigDecimal amount,
        String slot,
        String slotLabel,
        String status,
        String note
) {}
