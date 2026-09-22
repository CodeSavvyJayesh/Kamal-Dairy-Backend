package com.kamaldairy.kamal_dairy_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything the admin Insights tab draws, for one date range. Money is in
 * rupees. "previous" is the same number of days immediately before, so every
 * headline figure can show a change.
 */
public record SalesAnalyticsResponse(
        LocalDate from,
        LocalDate to,
        int days,
        Totals current,
        Totals previous,
        List<Day> daily,
        List<ProductRow> topProducts,
        List<PlanRow> plans,
        Customers customers,
        Payments payments,
        SubscriptionSnapshot subscriptions
) {

    /**
     * revenue = cart orders that were not cancelled + subscription deliveries
     * that were charged and not refunded.
     */
    public record Totals(
            BigDecimal revenue,
            BigDecimal cartRevenue,
            BigDecimal subscriptionRevenue,
            long orders,
            long deliveries,
            BigDecimal averageOrderValue,
            long cancelledOrders,
            BigDecimal refunded,
            long buyers,
            long newBuyers
    ) {}

    public record Day(
            LocalDate date,
            BigDecimal cartRevenue,
            BigDecimal subscriptionRevenue,
            BigDecimal revenue,
            long orders,
            long deliveries
    ) {}

    public record ProductRow(
            Integer productId,
            String name,
            long units,
            BigDecimal revenue,
            long cartUnits,
            long subscriptionUnits
    ) {}

    public record PlanRow(
            String plan,
            String planName,
            long deliveries,
            BigDecimal revenue,
            long activeSubscriptions
    ) {}

    /** newBuyers made their first ever purchase in the range; returning had bought before. */
    public record Customers(
            long buyers,
            long newBuyers,
            long returningBuyers,
            int repeatRatePercent
    ) {}

    /** How the range's revenue was paid. Subscriptions always come out of the wallet. */
    public record Payments(
            BigDecimal online,
            BigDecimal walletOrders,
            BigDecimal subscriptions
    ) {}

    public record SubscriptionSnapshot(
            long active,
            long paused,
            BigDecimal monthlyRecurringRevenue,
            BigDecimal walletFloat
    ) {}
}
