package com.kamaldairy.kamal_dairy_backend.dto;

import java.time.LocalDate;
import java.util.List;

public record SubscriptionDetailResponse(
        SubscriptionResponse subscription,
        List<CalendarDay> calendar,
        LocalDate firstEditableDate,
        int cutoffHour
) {}
