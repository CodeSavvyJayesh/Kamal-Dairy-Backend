package com.kamaldairy.kamal_dairy_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * How far the current balance will stretch across the customer's active
 * subscriptions, counting skips, vacations and pauses.
 */
public record WalletForecast(
        int activeSubscriptions,
        LocalDate nextChargeDate,
        BigDecimal nextChargeAmount,
        BigDecimal next7DaysAmount,
        LocalDate coveredUntil,
        int deliveriesCovered,
        boolean lowBalance
) {}
