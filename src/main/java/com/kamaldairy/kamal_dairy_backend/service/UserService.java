package com.kamaldairy.kamal_dairy_backend.service;

import com.kamaldairy.kamal_dairy_backend.dto.LoginRequest;
import com.kamaldairy.kamal_dairy_backend.dto.SignupRequest;
import com.kamaldairy.kamal_dairy_backend.dto.VerifyOtpRequest;
import com.kamaldairy.kamal_dairy_backend.exception.ApiException;
import com.kamaldairy.kamal_dairy_backend.exception.EmailAlreadyExistsException;
import com.kamaldairy.kamal_dairy_backend.exception.InvalidCredentialsException;
import com.kamaldairy.kamal_dairy_backend.exception.UserNotFoundException;
import com.kamaldairy.kamal_dairy_backend.model.User;
import com.kamaldairy.kamal_dairy_backend.repository.UserRepository;
import com.kamaldairy.kamal_dairy_backend.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class UserService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtUtil jwtUtil;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       EmailService emailService,
                       JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public void registerUser(SignupRequest request) {

        String email = normalise(request.getEmail());

        if (email.isEmpty() || !email.contains("@")) {
            throw new ApiException("A valid email address is required.", HttpStatus.BAD_REQUEST);
        }

        if (request.getPassword() == null || request.getPassword().length() < 8) {
            throw new ApiException("Password must be at least 8 characters.", HttpStatus.BAD_REQUEST);
        }

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        // Role is assigned by the server, never taken from the signup payload.
        // An admin is promoted deliberately in the database.
        user.setRole("ROLE_USER");

        String otp = String.valueOf(100000 + RANDOM.nextInt(900000));

        user.setEnabled(false);
        user.setOtp(passwordEncoder.encode(otp));
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(5));

        userRepository.save(user);

        emailService.sendOtpEmail(user.getEmail(), otp);
    }

    @Transactional
    public void verifyOtp(VerifyOtpRequest request) {

        User user = userRepository.findByEmail(normalise(request.getEmail()))
                .orElseThrow(UserNotFoundException::new);

        if (user.isEnabled()) {
            throw new ApiException("This account is already verified.", HttpStatus.BAD_REQUEST);
        }

        if (user.getOtp() == null || user.getOtpExpiry() == null
                || user.getOtpExpiry().isBefore(LocalDateTime.now())) {
            throw new ApiException("Your OTP has expired. Please sign up again.", HttpStatus.BAD_REQUEST);
        }

        if (request.getOtp() == null || !passwordEncoder.matches(request.getOtp(), user.getOtp())) {
            throw new ApiException("Invalid OTP.", HttpStatus.BAD_REQUEST);
        }

        user.setEnabled(true);
        user.setOtp(null);
        user.setOtpExpiry(null);

        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public String login(LoginRequest request) {

        User user = userRepository.findByEmail(normalise(request.getEmail()))
                .orElseThrow(InvalidCredentialsException::new);

        if (request.getPassword() == null
                || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        if (!user.isEnabled()) {
            throw new ApiException("Please verify your email before logging in.", HttpStatus.FORBIDDEN);
        }

        return jwtUtil.generateToken(user.getEmail(), user.getRole());
    }

    @Transactional(readOnly = true)
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(normalise(email))
                .orElseThrow(UserNotFoundException::new);
    }

    /**
     * Trim only. Deliberately NOT lower-cased: existing rows were saved with
     * whatever casing the user typed, so lower-casing here would lock those
     * accounts out. Normalising case needs a one-time UPDATE on the users table
     * first, then this method can be tightened.
     */
    private String normalise(String email) {
        return email == null ? "" : email.trim();
    }
}
