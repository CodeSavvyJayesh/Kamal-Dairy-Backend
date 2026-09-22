package com.kamaldairy.kamal_dairy_backend.service;

import com.kamaldairy.kamal_dairy_backend.model.SubscriptionFrequency;
import com.kamaldairy.kamal_dairy_backend.util.Money;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

import static com.kamaldairy.kamal_dairy_backend.model.SubscriptionFrequency.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests for the delivery-date rules and money maths. No Spring
 * context and no database, so they run in milliseconds with `mvn test`.
 */
class SubscriptionScheduleTest {

    /** A Wednesday. */
    private static final LocalDate START = LocalDate.of(2026, 9, 23);

    @Test
    void dailyDeliversEveryDayFromStart() {
        assertFalse(SubscriptionSchedule.occursOn(DAILY, 0, START, START.minusDays(1)));
        for (int i = 0; i < 40; i++) {
            assertTrue(SubscriptionSchedule.occursOn(DAILY, 0, START, START.plusDays(i)));
        }
        assertEquals(30, SubscriptionSchedule.count(DAILY, 0, START, START, START.plusDays(29)));
    }

    @Test
    void alternateDaysIsAnchoredToStartDate() {
        assertTrue(SubscriptionSchedule.occursOn(ALTERNATE_DAYS, 0, START, START));
        assertFalse(SubscriptionSchedule.occursOn(ALTERNATE_DAYS, 0, START, START.plusDays(1)));
        assertTrue(SubscriptionSchedule.occursOn(ALTERNATE_DAYS, 0, START, START.plusDays(2)));
        assertEquals(15, SubscriptionSchedule.count(ALTERNATE_DAYS, 0, START, START, START.plusDays(29)));
    }

    @Test
    void customDaysOnlyOnChosenWeekdays() {
        int monWedFri = SubscriptionSchedule.maskOf(EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));

        List<LocalDate> next = SubscriptionSchedule.next(CUSTOM_DAYS, monWedFri, START, START, 4);

        assertEquals(List.of(
                LocalDate.of(2026, 9, 23),   // Wed
                LocalDate.of(2026, 9, 25),   // Fri
                LocalDate.of(2026, 9, 28),   // Mon
                LocalDate.of(2026, 9, 30)),  // Wed
                next);
    }

    @Test
    void weeklyStartsOnTheFirstMatchingWeekday() {
        int saturday = SubscriptionSchedule.maskOf(EnumSet.of(DayOfWeek.SATURDAY));

        assertEquals(List.of(LocalDate.of(2026, 9, 26), LocalDate.of(2026, 10, 3)),
                SubscriptionSchedule.next(WEEKLY, saturday, START, START, 2));
    }

    @Test
    void monthlyIsEveryThirtyDays() {
        assertEquals(List.of(START, START.plusDays(30), START.plusDays(60)),
                SubscriptionSchedule.next(MONTHLY, 0, START, START, 3));
    }

    @Test
    void maskRoundTripsInCalendarOrder() {
        int mask = SubscriptionSchedule.maskOf(EnumSet.of(DayOfWeek.SUNDAY, DayOfWeek.MONDAY));
        assertEquals(List.of(DayOfWeek.MONDAY, DayOfWeek.SUNDAY), SubscriptionSchedule.daysOf(mask));
    }

    @Test
    void discountsMatchTheAdvertisedPlans() {
        assertEquals(0, DAILY.getDiscountPercent());
        assertEquals(8, WEEKLY.getDiscountPercent());
        assertEquals(10, MONTHLY.getDiscountPercent());
        assertEquals("Weekly Essentials", WEEKLY.getPlanName());
    }

    @Test
    void moneyIsExactToThePaisa() {
        assertEquals(34950, Money.toPaise(349.5));
        assertEquals(8096, Money.discounted(8800, 1, 8));      // Rs88 less 8%  = Rs80.96
        assertEquals(31455, Money.discounted(34950, 1, 10));   // Rs349.50 less 10% = Rs314.55
        assertEquals("80.96", Money.toRupees(8096).toPlainString());
        assertThrows(IllegalArgumentException.class, () -> Money.discounted(100, 1, 101));
    }

    @Test
    void forwardSearchIsBounded() {
        // A mask with no days can never match - the search must still stop.
        assertTrue(SubscriptionSchedule.next(CUSTOM_DAYS, 0, START, START, 5).isEmpty());
    }

    @Test
    void everyFrequencyIsClassifiedIntoAPlan() {
        for (SubscriptionFrequency f : SubscriptionFrequency.values()) {
            assertTrue(List.of("daily", "weekly", "monthly").contains(f.getPlan()), f.name());
        }
    }
}
