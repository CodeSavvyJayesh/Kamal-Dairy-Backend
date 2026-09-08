package com.kamaldairy.kamal_dairy_backend.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {
    public ResourceNotFoundException(String what) {
        super(what + " not found.", HttpStatus.NOT_FOUND);
    }
}
