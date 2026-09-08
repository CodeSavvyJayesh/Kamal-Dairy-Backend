package com.kamaldairy.kamal_dairy_backend.dto;

/**
 * What the browser needs to open Razorpay Checkout.
 * The amount is decided by the SERVER from the user's cart; the client no
 * longer gets to say what the order is worth. The key here is the publishable
 * key id (safe to expose) - the secret never leaves the backend.
 */
public class PaymentOrderResponse {

    private final String orderId;
    private final long amount;
    private final String currency;
    private final String key;

    public PaymentOrderResponse(String orderId, long amount, String currency, String key) {
        this.orderId = orderId;
        this.amount = amount;
        this.currency = currency;
        this.key = key;
    }

    public String getOrderId() { return orderId; }
    public long getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getKey() { return key; }
}
