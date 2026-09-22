package com.kamaldairy.kamal_dairy_backend.model;

/**
 * How often a subscription delivers, and which of the three advertised plans
 * it belongs to. The discount percentages live here and nowhere else, so the
 * marketing page, the price preview and the nightly charge can never disagree.
 */
public enum SubscriptionFrequency {

    DAILY("daily", "Every day", 0, 0, 0),
    ALTERNATE_DAYS("daily", "Alternate days", 0, 0, 0),
    CUSTOM_DAYS("daily", "Selected days", 0, 1, 7),
    WEEKLY("weekly", "Once a week", 8, 1, 1),
    MONTHLY("monthly", "Every 30 days", 10, 0, 0);

    private final String plan;
    private final String label;
    private final int discountPercent;
    private final int minDays;
    private final int maxDays;

    SubscriptionFrequency(String plan, String label, int discountPercent, int minDays, int maxDays) {
        this.plan = plan;
        this.label = label;
        this.discountPercent = discountPercent;
        this.minDays = minDays;
        this.maxDays = maxDays;
    }

    public String getPlan() { return plan; }
    public String getLabel() { return label; }
    public int getDiscountPercent() { return discountPercent; }

    /** Number of weekdays the customer must pick (0 = weekdays are not used). */
    public int getMinDays() { return minDays; }
    public int getMaxDays() { return maxDays; }

    public boolean usesWeekdays() { return maxDays > 0; }

    public String getPlanName() {
        return switch (plan) {
            case "weekly" -> "Weekly Essentials";
            case "monthly" -> "Monthly Smart Saver";
            default -> "Daily Delivery";
        };
    }
}
