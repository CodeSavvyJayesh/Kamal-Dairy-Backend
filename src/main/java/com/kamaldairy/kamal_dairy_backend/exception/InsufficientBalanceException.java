package com.kamaldairy.kamal_dairy_backend.exception;

import com.kamaldairy.kamal_dairy_backend.util.Money;
import org.springframework.http.HttpStatus;

/** 402 Payment Required - the frontend uses the status to offer a top-up. */
public class InsufficientBalanceException extends ApiException {

    public InsufficientBalanceException(long neededPaise, long balancePaise) {
        super("Not enough wallet balance. This needs " + Money.label(neededPaise)
                + " and your wallet has " + Money.label(balancePaise) + ".", HttpStatus.PAYMENT_REQUIRED);
    }
}
