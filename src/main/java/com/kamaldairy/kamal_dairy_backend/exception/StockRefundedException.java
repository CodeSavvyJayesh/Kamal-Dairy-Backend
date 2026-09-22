package com.kamaldairy.kamal_dairy_backend.exception;

import org.springframework.http.HttpStatus;

/**
 * 409 - an item sold out while the customer was paying with Razorpay. The
 * payment was real, so it has been credited to their wallet in the same
 * transaction. OrderService commits on this exception instead of rolling back.
 */
public class StockRefundedException extends ApiException {

    public StockRefundedException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
