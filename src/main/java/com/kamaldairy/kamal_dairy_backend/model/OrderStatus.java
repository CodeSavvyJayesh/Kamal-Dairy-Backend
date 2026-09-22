package com.kamaldairy.kamal_dairy_backend.model;

/**
 * Where a cart order is. It only ever moves forward:
 *
 *   PLACED -> CONFIRMED -> OUT_FOR_DELIVERY -> DELIVERED
 *
 * and can be CANCELLED from any open step (by the customer only while it is
 * still PLACED). DELIVERED and CANCELLED are final.
 */
public enum OrderStatus {

    PLACED("Placed"),
    CONFIRMED("Confirmed"),
    OUT_FOR_DELIVERY("Out for delivery"),
    DELIVERED("Delivered"),
    CANCELLED("Cancelled");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isOpen() {
        return this != DELIVERED && this != CANCELLED;
    }

    /** Forward moves only. Cancelling is a separate path because it refunds. */
    public boolean canAdvanceTo(OrderStatus next) {
        return isOpen() && next != null && next != CANCELLED && next.ordinal() > ordinal();
    }
}
