package com.kamaldairy.kamal_dairy_backend.dto;

/**
 * Proof-of-payment handed back by Razorpay Checkout in the browser.
 * All three fields are mandatory: the signature is an HMAC of
 * (order_id | payment_id) using the Razorpay secret, so it cannot be forged
 * by anyone who does not hold that secret.
 */
public class PlaceOrderRequest {

    private String razorpayOrderId;
    private String razorpayPaymentId;
    private String razorpaySignature;

    public PlaceOrderRequest() {}

    public String getRazorpayOrderId() { return razorpayOrderId; }
    public String getRazorpayPaymentId() { return razorpayPaymentId; }
    public String getRazorpaySignature() { return razorpaySignature; }

    public void setRazorpayOrderId(String razorpayOrderId) { this.razorpayOrderId = razorpayOrderId; }
    public void setRazorpayPaymentId(String razorpayPaymentId) { this.razorpayPaymentId = razorpayPaymentId; }
    public void setRazorpaySignature(String razorpaySignature) { this.razorpaySignature = razorpaySignature; }
}
