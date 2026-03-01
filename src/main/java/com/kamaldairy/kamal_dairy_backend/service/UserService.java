package com.kamaldairy.kamal_dairy_backend.service;

import com.kamaldairy.kamal_dairy_backend.dto.LoginRequest;
import com.kamaldairy.kamal_dairy_backend.dto.SignupRequest;
import com.kamaldairy.kamal_dairy_backend.dto.VerifyOtpRequest;
import com.kamaldairy.kamal_dairy_backend.model.User;
import com.kamaldairy.kamal_dairy_backend.repository.UserRepository;
import com.kamaldairy.kamal_dairy_backend.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    // =======================
    // SIGNUP
    // =======================
    public void registerUser(SignupRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole("ROLE_USER");

        // Generate OTP
        SecureRandom random = new SecureRandom();
        int otpNumber = 100000 + random.nextInt(900000);
        String otp = String.valueOf(otpNumber);

        // Hash OTP
        String hashedOtp = passwordEncoder.encode(otp);

        user.setEnabled(false);
        user.setOtp(hashedOtp);
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(5));

        userRepository.save(user);

        emailService.sendOtpEmail(user.getEmail(), otp);
    }

    // =======================
    // VERIFY OTP
    // =======================
    public void verifyOtp(VerifyOtpRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getOtpExpiry() == null ||
                user.getOtpExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("OTP expired");
        }

        if (!passwordEncoder.matches(request.getOtp(), user.getOtp())) {
            throw new RuntimeException("Invalid OTP");
        }

        user.setEnabled(true);
        user.setOtp(null);
        user.setOtpExpiry(null);

        userRepository.save(user);
    }

    // =======================
    // LOGIN
    // =======================
    public String login(LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.isEnabled()) {
            throw new RuntimeException("Please verify your email first");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid password");
        }

        return JwtUtil.generateToken(user.getEmail(), user.getRole());
    }
}