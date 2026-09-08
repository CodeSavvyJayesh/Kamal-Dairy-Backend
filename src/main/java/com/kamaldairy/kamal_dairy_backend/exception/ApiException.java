package com.kamaldairy.kamal_dairy_backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for every error this API raises deliberately.
 * Carries the HTTP status so GlobalExceptionHandler can map it without
 * a chain of instanceof checks, and so a business failure never leaks
 * out as a generic 500.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
