package com.kamaldairy.kamal_dairy_backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised whenever a payment cannot be proven genuine: bad signature,
 * unknown Razorpay order, an order belonging to a different user,
 * an amount that does not match the server-computed cart total, or a
 * payment reference that has already been used to place an order.
 */
public class PaymentVerificationException extends ApiException {
    public PaymentVerificationException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
