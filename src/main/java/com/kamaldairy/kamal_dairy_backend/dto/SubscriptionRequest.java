package com.kamaldairy.kamal_dairy_backend.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Used for preview, create and update. On update every field is optional and
 * only the ones sent are changed; productId and startDate are ignored there.
 */
public record SubscriptionRequest(
        Integer productId,
        Integer quantity,
        String frequency,
        List<String> daysOfWeek,
        String slot,
        LocalDate startDate,
        DeliveryAddress address
) {}
