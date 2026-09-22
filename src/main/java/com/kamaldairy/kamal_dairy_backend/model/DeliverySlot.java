package com.kamaldairy.kamal_dairy_backend.model;

public enum DeliverySlot {

    MORNING("Morning, 6 – 8 AM"),
    EVENING("Evening, 5 – 7 PM");

    private final String label;

    DeliverySlot(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
