package com.kamaldairy.kamal_dairy_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The one place that knows what "today" is and when changes stop being allowed.
 *
 * Rule (matches the FAQ on the site): changes for a date must be made before
 * the cutoff hour (default 23:00) on the previous evening. At the cutoff that
 * date is generated and charged, and from then on it is locked.
 */
@Component
public class DeliveryCalendar {

    private final Clock clock;
    private final int cutoffHour;

    public DeliveryCalendar(Clock clock, @Value("${app.subscription.cutoff-hour:23}") int cutoffHour) {
        if (cutoffHour < 1 || cutoffHour > 23) {
            throw new IllegalStateException("app.subscription.cutoff-hour must be between 1 and 23");
        }
        this.clock = clock;
        this.cutoffHour = cutoffHour;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public int cutoffHour() {
        return cutoffHour;
    }

    /** True once today's cutoff has passed - tomorrow is now locked. */
    public boolean isPastCutoff() {
        return now().getHour() >= cutoffHour;
    }

    /** The earliest date a customer can still change: tomorrow, or the day after once past cutoff. */
    public LocalDate firstEditableDate() {
        return today().plusDays(isPastCutoff() ? 2 : 1);
    }

    public boolean isEditable(LocalDate date) {
        return !date.isBefore(firstEditableDate());
    }
}
