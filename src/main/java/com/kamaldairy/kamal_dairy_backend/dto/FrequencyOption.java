package com.kamaldairy.kamal_dairy_backend.dto;

public record FrequencyOption(
        String code,
        String plan,
        String planName,
        String label,
        int discountPercent,
        int minDays,
        int maxDays
) {}
