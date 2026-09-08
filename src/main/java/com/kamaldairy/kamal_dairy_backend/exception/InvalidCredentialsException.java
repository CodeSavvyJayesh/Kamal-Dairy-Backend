package com.kamaldairy.kamal_dairy_backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends ApiException {
    public InvalidCredentialsException() {
        // Deliberately generic: never reveal whether the email exists or the
        // password was wrong, that difference is a user-enumeration oracle.
        super("Invalid email or password.", HttpStatus.UNAUTHORIZED);
    }
}
