package com.kamaldairy.kamal_dairy_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * state is one of:
 *   UPCOMING  - will be delivered and charged (editable: can be skipped)
 *   PENDING   - inside the cutoff, about to be generated (not editable)
 *   SKIPPED / VACATION / PAUSED - a delivery day that will not be sent
 *   SCHEDULED / DELIVERED / MISSED / REFUNDED - already generated
 *   NONE      - not a delivery day on this schedule
 */
public record CalendarDay(
        LocalDate date,
        String state,
        boolean editable,
        BigDecimal amount
) {}
