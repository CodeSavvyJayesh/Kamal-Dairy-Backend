package com.kamaldairy.kamal_dairy_backend.exception;

import org.springframework.http.HttpStatus;

public class EmailAlreadyExistsException extends ApiException {
    public EmailAlreadyExistsException(String email) {
        super("An account with " + email + " already exists.", HttpStatus.CONFLICT);
    }
}
