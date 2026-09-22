package com.kamaldairy.kamal_dairy_backend.exception;

import org.springframework.http.HttpStatus;

/** 429 with a Retry-After, so the client can tell the user how long to wait. */
public class TooManyRequestsException extends ApiException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(message, HttpStatus.TOO_MANY_REQUESTS);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
