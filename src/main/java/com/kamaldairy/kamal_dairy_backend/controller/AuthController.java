package com.kamaldairy.kamal_dairy_backend.controller;

import com.kamaldairy.kamal_dairy_backend.dto.*;
import com.kamaldairy.kamal_dairy_backend.security.ClientIp;
import com.kamaldairy.kamal_dairy_backend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")

public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    // =========================
    // SIGNUP (Send OTP)
    // =========================
    @PostMapping("/signup")
    public ResponseEntity<String> signup(@RequestBody SignupRequest request, HttpServletRequest http) {
        userService.registerUser(request, ClientIp.of(http));
        return ResponseEntity.ok("OTP sent to your email. Please verify.");
    }

    // =========================
    // VERIFY OTP
    // =========================
    @PostMapping("/verify")
    public ResponseEntity<String> verifyOtp(@RequestBody VerifyOtpRequest request, HttpServletRequest http) {
        userService.verifyOtp(request, ClientIp.of(http));
        return ResponseEntity.ok("Account verified successfully. You can now login.");
    }

    // =========================
    // LOGIN (FIXED)
    // =========================
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request, HttpServletRequest http) {
        return ResponseEntity.ok(userService.login(request, ClientIp.of(http)));
    }

    // =========================
    // RESEND VERIFICATION CODE
    // =========================
    @PostMapping("/resend-otp")
    public Map<String, String> resendOtp(@RequestBody EmailRequest request, HttpServletRequest http) {
        userService.resendOtp(request == null ? null : request.email(), ClientIp.of(http));
        return Map.of("message", "If this email is waiting to be verified, a new code is on its way.");
    }

    // =========================
    // FORGOT / RESET PASSWORD
    // =========================
    /** Same answer whether or not the email is registered. */
    @PostMapping("/forgot-password")
    public Map<String, String> forgotPassword(@RequestBody EmailRequest request, HttpServletRequest http) {
        userService.forgotPassword(request == null ? null : request.email(), ClientIp.of(http));
        return Map.of("message", "If an account exists for this email, we have sent a 6-digit code to it.");
    }

    /** Sets the new password and signs the user in. */
    @PostMapping("/reset-password")
    public LoginResponse resetPassword(@RequestBody ResetPasswordRequest request, HttpServletRequest http) {
        return userService.resetPassword(request, ClientIp.of(http));
    }
}