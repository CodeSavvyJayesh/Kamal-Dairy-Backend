package com.kamaldairy.kamal_dairy_backend.service;

import com.kamaldairy.kamal_dairy_backend.model.SubscriptionFrequency;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Pure date rules - no database, no clock, no Spring. Given a frequency, the
 * chosen weekdays and an anchor date, answers "is D a delivery day?".
 *
 * Kept free of dependencies so it can be unit tested exhaustively; see
 * SubscriptionScheduleTest.
 */
public final class SubscriptionSchedule {

    /** Hard stop for forward searches so a bad schedule can never loop forever. */
    public static final int MAX_SCAN_DAYS = 400;

    private SubscriptionSchedule() {}

    public static int bit(DayOfWeek day) {
        return 1 << (day.getValue() - 1);
    }

    public static int maskOf(Collection<DayOfWeek> days) {
        int mask = 0;
        for (DayOfWeek d : days) {
            mask |= bit(d);
        }
        return mask;
    }

    /** Monday-first, so the UI always shows days in calendar order. */
    public static List<DayOfWeek> daysOf(int mask) {
        List<DayOfWeek> days = new ArrayList<>();
        for (DayOfWeek d : DayOfWeek.values()) {
            if ((mask & bit(d)) != 0) {
                days.add(d);
            }
        }
        return days;
    }

    public static boolean occursOn(SubscriptionFrequency frequency, int daysMask, LocalDate start, LocalDate date) {
        if (date.isBefore(start)) {
            return false;
        }

        long offset = ChronoUnit.DAYS.between(start, date);

        return switch (frequency) {
            case DAILY -> true;
            case ALTERNATE_DAYS -> offset % 2 == 0;
            case CUSTOM_DAYS, WEEKLY -> (daysMask & bit(date.getDayOfWeek())) != 0;
            case MONTHLY -> offset % 30 == 0;
        };
    }

    /** Delivery dates on or after {@code from}, ignoring skips and pauses. */
    public static List<LocalDate> next(SubscriptionFrequency frequency, int daysMask, LocalDate start,
                                       LocalDate from, int count) {
        List<LocalDate> out = new ArrayList<>(count);
        LocalDate d = from.isBefore(start) ? start : from;

        for (int i = 0; i < MAX_SCAN_DAYS && out.size() < count; i++, d = d.plusDays(1)) {
            if (occursOn(frequency, daysMask, start, d)) {
                out.add(d);
            }
        }
        return out;
    }

    /** Number of delivery days in [from, to], inclusive. */
    public static int count(SubscriptionFrequency frequency, int daysMask, LocalDate start,
                            LocalDate from, LocalDate to) {
        int n = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (occursOn(frequency, daysMask, start, d)) {
                n++;
            }
        }
        return n;
    }
}
