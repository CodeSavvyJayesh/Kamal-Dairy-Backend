package com.kamaldairy.kamal_dairy_backend.dto;

import java.time.LocalDate;
import java.util.List;

public record SubscriptionPlansResponse(
        List<FrequencyOption> frequencies,
        List<SlotOption> slots,
        int cutoffHour,
        LocalDate firstEditableDate,
        int maxQuantity
) {}
