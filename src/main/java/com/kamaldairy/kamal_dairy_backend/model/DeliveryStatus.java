package com.kamaldairy.kamal_dairy_backend.model;

/**
 * SCHEDULED - generated and paid for from the wallet, on its way.
 * DELIVERED - handed over (marked by admin, or auto-confirmed the day after).
 * MISSED    - due, but not sent: low balance or product withdrawn. Never charged.
 * REFUNDED  - was paid for, then refunded to the wallet by an admin.
 */
public enum DeliveryStatus {
    SCHEDULED,
    DELIVERED,
    MISSED,
    REFUNDED
}
