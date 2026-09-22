package com.kamaldairy.kamal_dairy_backend.service;

import com.kamaldairy.kamal_dairy_backend.exception.ApiException;
import org.springframework.http.HttpStatus;

/** One password policy for signup and reset. 72 is BCrypt's real limit. */
public final class PasswordRules {

    public static final int MIN = 8;
    public static final int MAX = 72;

    private PasswordRules() {}

    public static void check(String password, String email) {
        if (password == null || password.length() < MIN) {
            throw bad("Password must be at least " + MIN + " characters.");
        }
        if (password.length() > MAX) {
            throw bad("Password can be at most " + MAX + " characters.");
        }
        if (!password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw bad("Use at least one letter and one number in your password.");
        }
        if (email != null && password.equalsIgnoreCase(email.trim())) {
            throw bad("Your password cannot be your email address.");
        }
    }

    private static ApiException bad(String message) {
        return new ApiException(message, HttpStatus.BAD_REQUEST);
    }
}
