package com.kamaldairy.kamal_dairy_backend.exception;

import org.springframework.http.HttpStatus;

/** 409 - the cart asks for more than is on the shelf. Nothing was charged. */
public class OutOfStockException extends ApiException {

    public OutOfStockException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
