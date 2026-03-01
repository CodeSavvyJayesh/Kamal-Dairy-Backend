package com.kamaldairy.kamal_dairy_backend.controller;

import com.kamaldairy.kamal_dairy_backend.dto.LoginRequest;
import com.kamaldairy.kamal_dairy_backend.dto.LoginResponse;
import com.kamaldairy.kamal_dairy_backend.dto.SignupRequest;
import com.kamaldairy.kamal_dairy_backend.dto.VerifyOtpRequest;
import com.kamaldairy.kamal_dairy_backend.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:5173")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    // =========================
    // SIGNUP (Send OTP)
    // =========================
    @PostMapping("/signup")
    public ResponseEntity<String> signup(@RequestBody SignupRequest request) {

        userService.registerUser(request);

        return ResponseEntity.ok("OTP sent to your email. Please verify.");
    }

    // =========================
    // VERIFY OTP
    // =========================
    @PostMapping("/verify")
    public ResponseEntity<String> verifyOtp(@RequestBody VerifyOtpRequest request) {

        userService.verifyOtp(request);

        return ResponseEntity.ok("Account verified successfully. You can now login.");
    }

    // =========================
    // LOGIN
    // =========================
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {

        String token = userService.login(request);

        return ResponseEntity.ok(new LoginResponse(token));
    }
}